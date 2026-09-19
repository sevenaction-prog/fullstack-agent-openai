package com.sevenaction.astra.meeting

object MeetingState {
    const val ACTION_START = "com.sevenaction.astra.START_MEETING"
    const val ACTION_STOP = "com.sevenaction.astra.STOP_MEETING"
    const val ACTION_PAUSE = "com.sevenaction.astra.PAUSE_MEETING"
    const val ACTION_MARK = "com.sevenaction.astra.MARK_MEETING"

    const val PREFS = "meeting_state"
    const val KEY_ACTIVE = "active"
    const val KEY_STARTED_AT = "started_at"
    const val KEY_FOLDER = "folder"
    const val KEY_ANALYSIS_PENDING = "analysis_pending"
}
