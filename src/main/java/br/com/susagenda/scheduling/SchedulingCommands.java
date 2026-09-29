package br.com.susagenda.scheduling;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import br.com.susagenda.shared.*;
import br.com.susagenda.audit.EventLog;
import static br.com.susagenda.shared.Database.*;
import static br.com.susagenda.shared.BusinessException.*;

/**
 * Escritas serializadas por agenda no banco: vale também com múltiplas
 * instâncias da aplicação.
 */
@Service
@Transactional
public class SchedulingCommands {
	private final Database db;
	private final EventLog events;
	private final Clock clock;
	private final Duration ttl;

	public SchedulingCommands(Database db, EventLog events, Clock clock, @Value("${app.offer-ttl}") Duration ttl) {
		if (ttl.isNegative() || ttl.isZero())
			throw new IllegalArgumentException("offer-ttl deve ser positivo");
		this.db = db;
		this.events = events;
		this.clock = clock;
		this.ttl = ttl;
	}

	private void lock(String agendaId) {
		db.one("SELECT id FROM agenda WHERE id=? FOR UPDATE", agendaId);
	}

	private String id() {
		return UUID.randomUUID().toString();
	}

	private Map<String, Object> slot(String id) {
		return db.one("SELECT * FROM slot WHERE id=?", id);
	}

	private Map<String, Object> offer(String id) {
		return db.one(
				"SELECT o.*,s.agenda_id,w.patient_id FROM offer o JOIN slot s ON s.id=o.slot_id JOIN wait_entry w ON w.id=o.wait_entry_id WHERE o.id=?",
				id);
	}

	private boolean is(Map<String, Object> row, String status) {
		return status.equals(row.get("status"));
	}

	private void future(Map<String, Object> slot) {
		if (!Instant.parse(text(slot, "startsAt")).isAfter(clock.instant()))
			throw conflict("O horário já começou.");
	}

	private void patientAvailable(String agendaId, String patientId) {
		db.one("SELECT id FROM patient WHERE id=?", patientId);
		if (db.count(
				"SELECT COUNT(*) FROM appointment a JOIN slot s ON s.id=a.slot_id WHERE s.agenda_id=? AND a.patient_id=? AND a.status='CONFIRMED' AND s.starts_at>?",
				agendaId, patientId, utc(clock.instant())) > 0)
			throw conflict("Paciente já possui agendamento futuro nesta agenda.");
		if (db.count(
				"SELECT COUNT(*) FROM wait_entry WHERE agenda_id=? AND patient_id=? AND status IN ('WAITING','OFFERED')",
				agendaId, patientId) > 0)
			throw conflict("Paciente já está na fila ou possui oferta pendente nesta agenda.");
	}

	public Map<String, Object> createSlot(String agendaId, Instant startsAt) {
		lock(agendaId);
		if (!startsAt.isAfter(clock.instant()))
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Informe um horário futuro.");
		String slotId = id();
		db.jdbc.update("INSERT INTO slot(id,agenda_id,starts_at,status) VALUES (?,?,?,'OPEN')", slotId, agendaId,
				utc(startsAt));
		events.append("SlotOpened", slotId);
		maintain(agendaId);
		return slot(slotId);
	}

	public Map<String, Object> book(String slotId, String patientId) {
		String agendaId = text(slot(slotId), "agendaId");
		lock(agendaId);
		var slot = slot(slotId);
		future(slot);
		// Uma fila ativa tem preferência sobre agendamento direto.
		if (db.count("SELECT COUNT(*) FROM wait_entry WHERE agenda_id=? AND status='WAITING'", agendaId) > 0)
			throw conflict("Há pacientes na fila. Entre na lista de espera.");
		if (!is(slot, "OPEN"))
			throw conflict("Vaga indisponível.");
		patientAvailable(agendaId, patientId);
		return createAppointment(slotId, patientId, "DIRECT");
	}

	private Map<String, Object> createAppointment(String slotId, String patientId, String source) {
		String appointmentId = id();
		db.jdbc.update(
				"INSERT INTO appointment(id,slot_id,patient_id,status,source,created_at) VALUES (?,?,?,'CONFIRMED',?,?)",
				appointmentId, slotId, patientId, source, utc(clock.instant()));
		db.jdbc.update("UPDATE slot SET status='BOOKED' WHERE id=?", slotId);
		events.append("AppointmentConfirmed", appointmentId);
		return db.one("SELECT * FROM appointment WHERE id=?", appointmentId);
	}

	public Map<String, Object> cancel(String appointmentId) {
		var initial = db.one("SELECT a.*,s.agenda_id FROM appointment a JOIN slot s ON s.id=a.slot_id WHERE a.id=?",
				appointmentId);
		String agendaId = text(initial, "agendaId");
		lock(agendaId);
		var appointment = db.one("SELECT * FROM appointment WHERE id=?", appointmentId);
		if (is(appointment, "CANCELLED"))
			return appointment; // retry seguro, mesmo que a vaga já tenha outro paciente
		String slotId = text(appointment, "slotId");
		future(slot(slotId));
		db.jdbc.update("UPDATE appointment SET status='CANCELLED' WHERE id=?", appointmentId);
		db.jdbc.update("UPDATE slot SET status='OPEN' WHERE id=?", slotId);
		events.append("AppointmentCancelled", appointmentId);
		maintain(agendaId);
		return db.one("SELECT * FROM appointment WHERE id=?", appointmentId);
	}

	public Map<String, Object> join(String agendaId, String patientId) {
		lock(agendaId);
		// Expira ofertas antes de validar nova entrada, permitindo reinserção ao fim da
		// fila.
		maintain(agendaId);
		patientAvailable(agendaId, patientId);
		String entryId = id();
		db.jdbc.update("INSERT INTO wait_entry(id,agenda_id,patient_id,status,created_at) VALUES (?,?,?,'WAITING',?)",
				entryId, agendaId, patientId, utc(clock.instant()));
		events.append("WaitlistJoined", entryId);
		dispatch(agendaId);
		return db.one("SELECT * FROM wait_entry WHERE id=?", entryId);
	}

	public Map<String, Object> withdraw(String entryId) {
		var initial = db.one("SELECT * FROM wait_entry WHERE id=?", entryId);
		lock(text(initial, "agendaId"));
		var entry = db.one("SELECT * FROM wait_entry WHERE id=?", entryId);
		if (is(entry, "WITHDRAWN"))
			return entry;
		if (!is(entry, "WAITING"))
			throw conflict("Só é possível sair de uma entrada WAITING. Para uma oferta, use recusar.");
		db.jdbc.update("UPDATE wait_entry SET status='WITHDRAWN' WHERE id=?", entryId);
		events.append("WaitlistWithdrawn", entryId);
		return db.one("SELECT * FROM wait_entry WHERE id=?", entryId);
	}

	public Map<String, Object> accept(String offerId) {
		var initial = offer(offerId);
		lock(text(initial, "agendaId"));
		var current = offer(offerId);
		if (is(current, "ACCEPTED"))
			return db.one("SELECT * FROM appointment WHERE id=?", text(current, "appointmentId"));
		if (!is(current, "PENDING"))
			throw conflict("A oferta não está mais pendente.");
		if (!Instant.parse(text(current, "expiresAt")).isAfter(clock.instant()))
			throw conflict("Oferta expirada. Aguarde o processamento automático da fila.");
		String slotId = text(current, "slotId");
		future(slot(slotId));
		var appointment = createAppointment(slotId, text(current, "patientId"), "WAITLIST");
		db.jdbc.update("UPDATE offer SET status='ACCEPTED',appointment_id=? WHERE id=?", appointment.get("id"),
				offerId);
		db.jdbc.update("UPDATE wait_entry SET status='BOOKED' WHERE id=?", current.get("waitEntryId"));
		events.append("OfferAccepted", offerId);
		return appointment;
	}

	public Map<String, Object> decline(String offerId) {
		var initial = offer(offerId);
		String agendaId = text(initial, "agendaId");
		lock(agendaId);
		var current = offer(offerId);
		if (is(current, "DECLINED"))
			return current;
		if (!is(current, "PENDING"))
			throw conflict("A oferta não está mais pendente.");
		if (!Instant.parse(text(current, "expiresAt")).isAfter(clock.instant()))
			throw conflict("Oferta expirada. Aguarde o processamento automático da fila.");
		closeOffer(current, "DECLINED", "OfferDeclined");
		maintain(agendaId);
		return offer(offerId);
	}

	/**
	 * Chamado pelo job e por operações que disponibilizam vagas. Uma transação por
	 * agenda.
	 */
	public void processAgenda(String agendaId) {
		lock(agendaId);
		maintain(agendaId);
	}

	private void maintain(String agendaId) {
		var expired = db.rows(
				"SELECT o.* FROM offer o JOIN slot s ON s.id=o.slot_id WHERE s.agenda_id=? AND o.status='PENDING' AND o.expires_at<=?",
				agendaId, utc(clock.instant()));
		for (var item : expired)
			closeOffer(item, "EXPIRED", "OfferExpired");
		dispatch(agendaId);
	}

	private void closeOffer(Map<String, Object> current, String status, String event) {
		db.jdbc.update("UPDATE offer SET status=? WHERE id=?", status, current.get("id"));
		db.jdbc.update("UPDATE wait_entry SET status=? WHERE id=?", status, current.get("waitEntryId"));
		db.jdbc.update("UPDATE slot SET status='OPEN' WHERE id=?", current.get("slotId"));
		events.append(event, text(current, "id"));
	}

	private void dispatch(String agendaId) {
		var available = db.rows(
				"SELECT * FROM slot WHERE agenda_id=? AND status='OPEN' AND starts_at>? ORDER BY starts_at,id",
				agendaId, utc(clock.instant()));
		for (var slot : available) {
			var waiting = db.rows(
					"SELECT * FROM wait_entry WHERE agenda_id=? AND status='WAITING' ORDER BY sequence_no LIMIT 1",
					agendaId);
			if (waiting.isEmpty())
				return;
			var entry = waiting.get(0);
			String offerId = id();
			Instant startsAt = Instant.parse(text(slot, "startsAt"));
			Instant expires = clock.instant().plus(ttl);
			if (expires.isAfter(startsAt))
				expires = startsAt;
			db.jdbc.update(
					"INSERT INTO offer(id,slot_id,wait_entry_id,status,expires_at,created_at) VALUES (?,?,?,'PENDING',?,?)",
					offerId, slot.get("id"), entry.get("id"), utc(expires), utc(clock.instant()));
			db.jdbc.update("UPDATE slot SET status='RESERVED' WHERE id=?", slot.get("id"));
			db.jdbc.update("UPDATE wait_entry SET status='OFFERED' WHERE id=?", entry.get("id"));
			events.append("OfferCreated", offerId);
		}
	}
}
