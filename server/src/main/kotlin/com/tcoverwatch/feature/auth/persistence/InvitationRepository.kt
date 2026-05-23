package com.tcoverwatch.feature.auth.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InvitationRepository : JpaRepository<Invitation, UUID> {
    fun findByToken(token: UUID): Invitation?

    // FIFO when an email has multiple pending invitations — auth gate picks the oldest.
    fun findFirstByEmailAndAcceptedAtIsNullOrderByCreatedAtAsc(email: String): Invitation?
}
