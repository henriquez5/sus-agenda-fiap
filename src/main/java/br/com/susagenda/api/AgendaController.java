package br.com.susagenda.api;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static br.com.susagenda.api.Responses.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import br.com.susagenda.catalog.CatalogService;
import br.com.susagenda.scheduling.SchedulingCommands;
import br.com.susagenda.reporting.AgendaQueries;

@RestController
@RequestMapping(value = "/api", produces = "application/json")
public class AgendaController {
	public record PatientRequest(@NotBlank @Size(max = 120) String name) {
	}

	public record AgendaRequest(@NotBlank @Size(max = 120) String unitName,
			@NotBlank @Size(max = 80) String specialty) {
	}

	public record SlotRequest(@NotNull Instant startsAt) {
	}

	public record PatientIdRequest(@NotBlank @Size(max = 36) String patientId) {
	}

	private final ObjectMapper mapper;
	private final CatalogService catalog;
	private final SchedulingCommands commands;
	private final AgendaQueries queries;

	public AgendaController(CatalogService catalog, SchedulingCommands commands, AgendaQueries queries,
			ObjectMapper mapper) {
		this.catalog = catalog;
		this.commands = commands;
		this.queries = queries;
		this.mapper = mapper;
	}

	private <T> T response(Map<String, Object> row, Class<T> type) {
		return mapper.convertValue(row, type);
	}

	private <T> List<T> responses(List<Map<String, Object>> rows, Class<T> type) {
		return rows.stream().map(row -> response(row, type)).toList();
	}

	@PostMapping("/patients")
	@ResponseStatus(HttpStatus.CREATED)
	public PatientResponse patient(@Valid @RequestBody PatientRequest body) {
		return response(catalog.patient(body.name()), PatientResponse.class);
	}

	@GetMapping("/patients")
	public List<PatientResponse> patients() {
		return responses(queries.patients(), PatientResponse.class);
	}

	@PostMapping("/agendas")
	@ResponseStatus(HttpStatus.CREATED)
	public AgendaResponse agenda(@Valid @RequestBody AgendaRequest body) {
		return response(catalog.agenda(body.unitName(), body.specialty()), AgendaResponse.class);
	}

	@GetMapping("/agendas")
	public List<AgendaResponse> agendas() {
		return responses(queries.agendas(), AgendaResponse.class);
	}

	@PostMapping("/agendas/{id}/slots")
	@ResponseStatus(HttpStatus.CREATED)
	public SlotResponse slot(@PathVariable String id, @Valid @RequestBody SlotRequest body) {
		return response(commands.createSlot(id, body.startsAt()), SlotResponse.class);
	}

	@GetMapping("/agendas/{id}/slots")
	public List<SlotResponse> slots(@PathVariable String id) {
		return responses(queries.slots(id), SlotResponse.class);
	}

	@PostMapping("/slots/{id}/book")
	@ResponseStatus(HttpStatus.CREATED)
	public AppointmentResponse book(@PathVariable String id, @Valid @RequestBody PatientIdRequest body) {
		return response(commands.book(id, body.patientId()), AppointmentResponse.class);
	}

	@PostMapping("/appointments/{id}/cancel")
	public AppointmentResponse cancel(@PathVariable String id) {
		return response(commands.cancel(id), AppointmentResponse.class);
	}

	@GetMapping("/patients/{id}/appointments")
	public List<AppointmentDetailsResponse> appointments(@PathVariable String id) {
		return responses(queries.appointments(id), AppointmentDetailsResponse.class);
	}

	@PostMapping("/agendas/{id}/waitlist")
	@ResponseStatus(HttpStatus.CREATED)
	public WaitEntryResponse join(@PathVariable String id, @Valid @RequestBody PatientIdRequest body) {
		return response(commands.join(id, body.patientId()), WaitEntryResponse.class);
	}

	@GetMapping("/agendas/{id}/waitlist")
	public List<WaitEntryResponse> waitlist(@PathVariable String id) {
		return responses(queries.waitlist(id), WaitEntryResponse.class);
	}

	@PostMapping("/waitlist/{id}/withdraw")
	public WaitEntryResponse withdraw(@PathVariable String id) {
		return response(commands.withdraw(id), WaitEntryResponse.class);
	}

	@GetMapping("/agendas/{id}/offers")
	public List<OfferDetailsResponse> offers(@PathVariable String id) {
		return responses(queries.offers(id), OfferDetailsResponse.class);
	}

	@PostMapping("/offers/{id}/accept")
	public AppointmentResponse accept(@PathVariable String id) {
		return response(commands.accept(id), AppointmentResponse.class);
	}

	@PostMapping("/offers/{id}/decline")
	public OfferResponse decline(@PathVariable String id) {
		return response(commands.decline(id), OfferResponse.class);
	}

	@GetMapping("/dashboard")
	public DashboardResponse dashboard() {
		return response(queries.dashboard(), DashboardResponse.class);
	}

	@GetMapping("/events")
	public List<EventResponse> events() {
		return responses(queries.events(), EventResponse.class);
	}
}
