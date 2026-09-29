package br.com.susagenda.scheduling;

import java.time.Clock;
import br.com.susagenda.shared.Database;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.slf4j.LoggerFactory;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ExpirationJob {
	private final Database db;
	private final SchedulingCommands commands;
	private final Clock clock;

	public ExpirationJob(Database db, SchedulingCommands commands, Clock clock) {
		this.db = db;
		this.commands = commands;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.expiration-delay-ms:5000}")
	public void expire() {
		var agendas = db.rows(
				"SELECT DISTINCT s.agenda_id FROM offer o JOIN slot s ON s.id=o.slot_id WHERE o.status='PENDING' AND o.expires_at<=?",
				Database.utc(clock.instant()));
		for (var agenda : agendas) {
			try {
				commands.processAgenda((String) agenda.get("agendaId"));
			} catch (RuntimeException e) {
				LoggerFactory.getLogger(getClass()).error("Falha ao processar expiração na agenda {}",
						agenda.get("agendaId"), e);
			}
		}
	}
}
