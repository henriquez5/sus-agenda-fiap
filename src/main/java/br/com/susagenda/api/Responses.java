package br.com.susagenda.api;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contratos de saída explícitos para serialização JSON e documentação OpenAPI.
 */
public final class Responses {
	private Responses() {
	}

	public record PatientResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "Ana Fictícia") String name,
			@Schema(example = "2030-01-01T15:00:00Z") Instant createdAt) {
	}

	public record AgendaResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "UBS Centro") String unitName, @Schema(example = "Clínica geral") String specialty,
			@Schema(example = "2030-01-01T15:00:00Z") Instant createdAt) {
	}

	public record SlotResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String agendaId,
			@Schema(example = "2030-01-01T15:00:00Z") Instant startsAt, @Schema(example = "OPEN", allowableValues = {
					"OPEN", "RESERVED", "BOOKED" }) String status) {
	}

	public record AppointmentResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String slotId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String patientId,
			@Schema(example = "CONFIRMED", allowableValues = {
					"CONFIRMED", "CANCELLED" }) String status,
			@Schema(example = "WAITLIST", allowableValues = { "DIRECT", "WAITLIST" }) String source,
			@Schema(example = "2030-01-01T15:00:00Z") Instant createdAt) {
	}

	public record AppointmentDetailsResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String slotId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String patientId,
			@Schema(example = "CONFIRMED", allowableValues = {
					"CONFIRMED", "CANCELLED" }) String status,
			@Schema(example = "WAITLIST", allowableValues = { "DIRECT", "WAITLIST" }) String source,
			@Schema(example = "2030-01-01T15:00:00Z") Instant createdAt,
			@Schema(example = "2030-01-01T15:00:00Z") Instant startsAt,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String agendaId) {
	}

	public record WaitEntryResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "1") long sequenceNo,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String agendaId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String patientId,
			@Schema(example = "WAITING", allowableValues = {
					"WAITING", "OFFERED", "BOOKED", "EXPIRED", "DECLINED", "WITHDRAWN" }) String status,
			@Schema(example = "2030-01-01T15:00:00Z") Instant createdAt) {
	}

	public record OfferResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String slotId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String waitEntryId,
			@Schema(example = "PENDING", allowableValues = {
					"PENDING", "ACCEPTED", "EXPIRED", "DECLINED" }) String status,
			@Schema(example = "2030-01-01T15:00:00Z") Instant expiresAt,
			@Schema(example = "2030-01-01T15:00:00Z") Instant createdAt,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String appointmentId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String agendaId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String patientId) {
	}

	public record OfferDetailsResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String slotId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String waitEntryId,
			@Schema(example = "PENDING", allowableValues = {
					"PENDING", "ACCEPTED", "EXPIRED", "DECLINED" }) String status,
			@Schema(example = "2030-01-01T15:00:00Z") Instant expiresAt,
			@Schema(example = "2030-01-01T15:00:00Z") Instant createdAt,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String appointmentId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String patientId,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String agendaId,
			@Schema(example = "2030-01-01T15:00:00Z") Instant startsAt) {
	}

	public record DashboardResponse(@Schema(example = "1") long confirmedAppointments,
			@Schema(example = "1") long cancelledAppointments, @Schema(example = "1") long waitlistConfirmations,
			@Schema(example = "1") long waitingPatients, @Schema(example = "1") long pendingOffers,
			@Schema(example = "1") long expiredOffers) {
	}

	public record EventResponse(@Schema(example = "11111111-1111-4111-8111-111111111111") String id,
			@Schema(example = "1") long sequenceNo, @Schema(example = "OfferCreated") String eventType,
			@Schema(example = "11111111-1111-4111-8111-111111111111") String aggregateId,
			@Schema(example = "operador") String actor, @Schema(example = "2030-01-01T15:00:00Z") Instant occurredAt) {
	}
}
