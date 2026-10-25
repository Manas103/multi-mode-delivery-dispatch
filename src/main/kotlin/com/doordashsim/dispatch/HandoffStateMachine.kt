package com.doordashsim.dispatch

/** The six states of a merchant handoff, in the only order they may occur. */
enum class HandoffState(val sequence: Long) {
    CREATED(1),
    NOTIFIED(2),
    MERCHANT_ACK(3),
    READY_FOR_PICKUP(4),
    HANDED_OFF(5),
    COMPLETED(6);

    companion object {
        fun forSequence(sequence: Long): HandoffState? = entries.firstOrNull { it.sequence == sequence }
    }
}

object HandoffStateMachine {
    /** A transition is valid only if it advances the order exactly one step past its last applied sequence. */
    fun isNextStep(lastAppliedSequence: Long, candidateSequence: Long): Boolean =
        candidateSequence == lastAppliedSequence + 1
}
