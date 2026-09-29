package br.com.susagenda.audit;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import br.com.susagenda.shared.Database;

/** Histórico transacional de eventos; não é um Event Store para reconstrução de estado. */
@Component
public class EventLog {
	private static final Logger log = LoggerFactory.getLogger(EventLog.class);
	private final Database db;
	private final Clock clock;

	public EventLog(Database db, Clock clock) {
		this.db = db;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void append(String type, String aggregateId) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		String actor = authentication == null ? "scheduler" : authentication.getName();
		db.jdbc.update("INSERT INTO domain_event(id,event_type,aggregate_id,actor,occurred_at) VALUES (?,?,?,?,?)",
				UUID.randomUUID().toString(), type, aggregateId, actor, Database.utc(clock.instant()));
		log.info("domain_event={} aggregate_id={} actor={}", type, aggregateId, actor);
	}
}
