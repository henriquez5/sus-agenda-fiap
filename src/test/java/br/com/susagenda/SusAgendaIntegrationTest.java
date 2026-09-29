package br.com.susagenda;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import br.com.susagenda.catalog.CatalogService;
import br.com.susagenda.scheduling.SchedulingCommands;
import br.com.susagenda.reporting.AgendaQueries;
import br.com.susagenda.shared.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest(properties={"app.scheduler-enabled=false","app.offer-ttl=PT1M"})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Import(SusAgendaIntegrationTest.TimeConfig.class)
class SusAgendaIntegrationTest {
    static class TestClock extends Clock {
        private Instant value=Instant.parse("2030-01-01T12:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return value;}
        void reset(){value=Instant.parse("2030-01-01T12:00:00Z");}
        void advance(Duration duration){value=value.plus(duration);}
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary TestClock testClock(){return new TestClock();} }
    @Autowired CatalogService catalog;
    @Autowired SchedulingCommands commands;
    @Autowired AgendaQueries queries;
    @Autowired Database db;
    @Autowired TestClock clock;
    @Autowired MockMvc mvc;
    String agenda,p1,p2,p3,slot;
    static String id(Map<String,Object> row){return (String)row.get("id");}
    @BeforeEach void setup(){
        for(String table:List.of("domain_event","offer","wait_entry","appointment","slot","agenda","patient")) db.jdbc.update("DELETE FROM "+table);
        clock.reset();
        agenda=id(catalog.agenda("UBS Teste","Clínica geral"));
        p1=id(catalog.patient("Paciente A"));p2=id(catalog.patient("Paciente B"));p3=id(catalog.patient("Paciente C"));
        slot=id(commands.createSlot(agenda,clock.instant().plus(Duration.ofDays(1))));
    }
    Map<String,Object> pending(){return queries.offers(agenda).stream().filter(o->"PENDING".equals(o.get("status"))).findFirst().orElseThrow();}
    String cancelWithQueue(){
        String appointment=id(commands.book(slot,p1));commands.join(agenda,p2);commands.join(agenda,p3);commands.cancel(appointment);return appointment;
    }
    @Test void cancellationOffersToFirstAndConfirmationIsIdempotent(){
        cancelWithQueue();var offer=pending();assertThat(offer.get("patientId")).isEqualTo(p2);
        var appointment=commands.accept(id(offer));assertThat(appointment.get("source")).isEqualTo("WAITLIST");
        assertThat(commands.accept(id(offer))).isEqualTo(appointment);
        assertThat(queries.dashboard().get("waitlistConfirmations")).isEqualTo(1L);
        assertThat(db.count("SELECT COUNT(*) FROM domain_event WHERE event_type='OfferAccepted'")).isEqualTo(1);
    }
    @Test void duplicateCancellationDoesNotCancelNewPatient(){
        String original=cancelWithQueue();var second=commands.accept(id(pending()));
        commands.cancel(original);
        assertThat(db.one("SELECT * FROM appointment WHERE id=?",id(second)).get("status")).isEqualTo("CONFIRMED");
        assertThat(db.one("SELECT * FROM slot WHERE id=?",slot).get("status")).isEqualTo("BOOKED");
    }
    @Test void expiryMovesOfferToNextPatient(){
        cancelWithQueue();String first=id(pending());clock.advance(Duration.ofMinutes(1));commands.processAgenda(agenda);
        assertThat(pending().get("patientId")).isEqualTo(p3);
        assertThat(db.one("SELECT * FROM offer WHERE id=?",first).get("status")).isEqualTo("EXPIRED");
        assertThatThrownBy(()->commands.accept(first)).isInstanceOf(BusinessException.class);
    }
    @Test void expiredOfferCannotBeAcceptedBeforeJobRuns(){
        cancelWithQueue();String first=id(pending());clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(()->commands.accept(first)).isInstanceOf(BusinessException.class);
        commands.processAgenda(agenda);assertThat(pending().get("patientId")).isEqualTo(p3);
    }
    @Test void declineOffersToNextAndRetryIsSafe(){
        cancelWithQueue();String first=id(pending());commands.decline(first);commands.decline(first);
        assertThat(pending().get("patientId")).isEqualTo(p3);
        assertThat(db.count("SELECT COUNT(*) FROM offer WHERE status='PENDING'")).isEqualTo(1);
    }
    @Test void noQueueMakesCancelledSlotAvailable(){
        var booked=commands.book(slot,p1);commands.cancel(id(booked));
        assertThat(db.one("SELECT * FROM slot WHERE id=?",slot).get("status")).isEqualTo("OPEN");
        assertThat(commands.book(slot,p2).get("patientId")).isEqualTo(p2);
    }
    @Test void reservedSlotCannotBeBookedDirectly(){
        cancelWithQueue();assertThatThrownBy(()->commands.book(slot,p3)).isInstanceOf(BusinessException.class);
    }
    @Test void duplicateQueueAndAlreadyBookedPatientAreRejected(){
        commands.book(slot,p1);commands.join(agenda,p2);
        assertThatThrownBy(()->commands.join(agenda,p2)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->commands.join(agenda,p1)).isInstanceOf(BusinessException.class);
    }
    @Test void newSlotOffersToWaitingPatient(){
        commands.book(slot,p1);commands.join(agenda,p2);
        commands.createSlot(agenda,clock.instant().plus(Duration.ofDays(2)));
        assertThat(pending().get("patientId")).isEqualTo(p2);
    }
    @Test void agendaIsolatesSpecialtiesAndUnits(){
        String other=id(catalog.agenda("Outra UBS","Clínica geral"));commands.join(other,p3);
        String appt=id(commands.book(slot,p1));commands.join(agenda,p2);commands.cancel(appt);
        assertThat(pending().get("patientId")).isEqualTo(p2);
        assertThat(queries.offers(other)).isEmpty();
    }
    @Test void pastSlotsAndOffersAreNotReassigned(){
        String near=id(commands.createSlot(agenda,clock.instant().plusSeconds(10)));
        commands.book(slot,p1);commands.book(near,p2);commands.join(agenda,p3);
        String appointment=id(queries.appointments(p2).get(0));commands.cancel(appointment);
        assertThat(Instant.parse((String)pending().get("expiresAt"))).isEqualTo(clock.instant().plusSeconds(10));
        clock.advance(Duration.ofSeconds(11));commands.processAgenda(agenda);
        assertThat(queries.offers(agenda).stream().filter(o->"PENDING".equals(o.get("status")))).isEmpty();
        assertThatThrownBy(()->commands.createSlot(agenda,clock.instant().minusSeconds(1))).isInstanceOf(BusinessException.class);
    }
    @Test void expiredPatientCanRejoinAtEnd(){
        cancelWithQueue();clock.advance(Duration.ofMinutes(1));commands.processAgenda(agenda);
        var rejoined=commands.join(agenda,p2);assertThat(rejoined.get("status")).isEqualTo("WAITING");
        assertThat(pending().get("patientId")).isEqualTo(p3);
    }
    @Test void withdrawingWaitingEntryRemovesItFromSelection(){
        String original=id(commands.book(slot,p1));var entry=commands.join(agenda,p2);commands.join(agenda,p3);
        commands.withdraw(id(entry));commands.cancel(original);assertThat(pending().get("patientId")).isEqualTo(p3);
    }
    @Test void transactionRollsBackWhenPatientDoesNotExist(){
        long before=db.count("SELECT COUNT(*) FROM domain_event");
        assertThatThrownBy(()->commands.book(slot,UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);
        assertThat(db.count("SELECT COUNT(*) FROM appointment")).isZero();
        assertThat(db.count("SELECT COUNT(*) FROM domain_event")).isEqualTo(before);
        assertThat(commands.book(slot,p1).get("status")).isEqualTo("CONFIRMED");
    }
    @Test void twoConcurrentBookingsProduceOneWinner() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Boolean>>();
            for(String patient:List.of(p1,p2)) futures.add(executor.submit(()->{start.await();try{commands.book(slot,patient);return true;}catch(BusinessException e){return false;}}));
            start.countDown();int successes=0;for(var f:futures) if(f.get(10,TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(db.count("SELECT COUNT(*) FROM appointment WHERE status='CONFIRMED'")).isEqualTo(1);
        } finally {executor.shutdownNow();}
    }
    @Test void concurrentAcceptsReturnSameAppointment() throws Exception {
        cancelWithQueue();String offer=id(pending());var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var one=executor.submit(()->{start.await();return id(commands.accept(offer));});
            var two=executor.submit(()->{start.await();return id(commands.accept(offer));});
            start.countDown();assertThat(one.get(10,TimeUnit.SECONDS)).isEqualTo(two.get(10,TimeUnit.SECONDS));
            assertThat(db.count("SELECT COUNT(*) FROM appointment WHERE source='WAITLIST'")).isEqualTo(1);
        }finally{executor.shutdownNow();}
    }
    @Test void securitySeparatesAnonymousReaderAndOperator() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").with(httpBasic("leitor","leitor-demo"))).andExpect(status().isOk());
        mvc.perform(post("/api/patients").with(httpBasic("leitor","leitor-demo")).contentType("application/json").content("{\"name\":\"Teste\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/patients").with(httpBasic("operador","operador-demo")).contentType("application/json").content("{\"name\":\"Teste\"}")).andExpect(status().isCreated());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
    @Test void invalidPayloadAndMissingResourcesHaveUsefulStatuses() throws Exception {
        mvc.perform(post("/api/patients").with(httpBasic("operador","operador-demo")).contentType("application/json").content("{\"name\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/appointments/missing/cancel").with(httpBasic("operador","operador-demo"))).andExpect(status().isNotFound());
    }
    @Test void swaggerDocumentsConcreteResponseFields() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.WaitEntryResponse.properties.patientId.type").value("string"))
            .andExpect(jsonPath("$.components.schemas.WaitEntryResponse.properties.sequenceNo.type").value("integer"))
            .andExpect(jsonPath("$.components.schemas.WaitEntryResponse.properties.createdAt.format").value("date-time"))
            .andExpect(jsonPath("$.components.schemas.WaitEntryResponse.additionalProperties").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/waitlist/{id}/withdraw'].post.responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/WaitEntryResponse"));
    }
}
