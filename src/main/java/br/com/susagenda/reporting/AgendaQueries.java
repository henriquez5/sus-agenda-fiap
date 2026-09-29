package br.com.susagenda.reporting;

import java.util.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.susagenda.shared.Database;

/** Separação lógica de leitura e escrita, compartilhando o mesmo banco. */
@Service
@Transactional(readOnly = true)
public class AgendaQueries {
	private final Database db;
	private final Clock clock;

	public AgendaQueries(Database db, Clock clock) {
		this.db = db;
		this.clock = clock;
	}

	public List<Map<String, Object>> patients() {
		return db.rows("SELECT * FROM patient ORDER BY created_at,id LIMIT 200");
	}

	public List<Map<String, Object>> agendas() {
		return db.rows("SELECT * FROM agenda ORDER BY unit_name,specialty LIMIT 200");
	}

	public List<Map<String, Object>> slots(String agendaId) {
		return db.rows("SELECT * FROM slot WHERE agenda_id=? ORDER BY starts_at LIMIT 200", agendaId);
	}

	public List<Map<String, Object>> waitlist(String agendaId) {
		return db.rows("SELECT * FROM wait_entry WHERE agenda_id=? ORDER BY sequence_no LIMIT 200", agendaId);
	}

	public List<Map<String, Object>> offers(String agendaId) {
		return db.rows(
				"SELECT o.*,w.patient_id,s.agenda_id,s.starts_at FROM offer o JOIN slot s ON s.id=o.slot_id JOIN wait_entry w ON w.id=o.wait_entry_id WHERE s.agenda_id=? ORDER BY o.created_at,o.id LIMIT 200",
				agendaId);
	}

	public List<Map<String, Object>> appointments(String patientId) {
		return db.rows(
				"SELECT a.*,s.starts_at,s.agenda_id FROM appointment a JOIN slot s ON s.id=a.slot_id WHERE a.patient_id=? ORDER BY a.created_at,a.id LIMIT 200",
				patientId);
	}

	public List<Map<String, Object>> events() {
		return db.rows("SELECT * FROM domain_event ORDER BY sequence_no DESC LIMIT 200");
	}

	public Map<String, Object> dashboard() {
		return Map.of("confirmedAppointments", db.count("SELECT COUNT(*) FROM appointment WHERE status='CONFIRMED'"),
				"cancelledAppointments", db.count("SELECT COUNT(*) FROM appointment WHERE status='CANCELLED'"),
				"waitlistConfirmations",
				db.count("SELECT COUNT(*) FROM appointment WHERE source='WAITLIST' AND status='CONFIRMED'"),
				"waitingPatients", db.count("SELECT COUNT(*) FROM wait_entry WHERE status='WAITING'"), "pendingOffers",
				db.count("SELECT COUNT(*) FROM offer WHERE status='PENDING' AND expires_at>?",
						Database.utc(clock.instant())),
				"expiredOffers", db.count("SELECT COUNT(*) FROM offer WHERE status='EXPIRED'"));
	}
}
