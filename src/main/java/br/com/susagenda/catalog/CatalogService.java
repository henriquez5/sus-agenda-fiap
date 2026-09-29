package br.com.susagenda.catalog;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.susagenda.shared.Database;
import br.com.susagenda.audit.EventLog;

@Service
public class CatalogService {
	private final Database db;
	private final EventLog events;
	private final Clock clock;

	public CatalogService(Database db, EventLog events, Clock clock) {
		this.db = db;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public Map<String, Object> patient(String name) {
		String id = UUID.randomUUID().toString();
		db.jdbc.update("INSERT INTO patient(id,name,created_at) VALUES (?,?,?)", id, name.strip(),
				Database.utc(clock.instant()));
		events.append("PatientRegistered", id);
		return db.one("SELECT * FROM patient WHERE id=?", id);
	}

	@Transactional
	public Map<String, Object> agenda(String unitName, String specialty) {
		String id = UUID.randomUUID().toString();
		db.jdbc.update("INSERT INTO agenda(id,unit_name,specialty,created_at) VALUES (?,?,?,?)", id, unitName.strip(),
				specialty.strip(), Database.utc(clock.instant()));
		events.append("AgendaCreated", id);
		return db.one("SELECT * FROM agenda WHERE id=?", id);
	}
}
