package com.example.service

/**
 * Explicit lifecycle state machine for card testing.
 * Enforces that a worker can ONLY advance to the next card when in READY_FOR_NEXT state.
 */
enum class CardLifecycleState {
    IDLE,
    QUEUED,
    NAVIGATING,
    LOGIN_PAGE_READY,
    SUBMITTING,
    WAITING_RESULT,
    SUCCESS,
    FAILURE,
    TIMEOUT,
    NETWORK_ERROR,
    ENGINE_ERROR,
    LOGOUT_REQUESTED,
    LOGOUT_CONFIRMED,
    RESETTING,
    READY_FOR_NEXT,
    FAILED_BARRIER
}
