package com.comicreader.domain.usecase

import com.comicreader.domain.repository.ComicRepository
import com.comicreader.domain.repository.StatsRepository
import javax.inject.Inject

class SaveReadingProgressUseCase @Inject constructor(
    private val comicRepository: ComicRepository,
    private val statsRepository: StatsRepository
) {
    suspend operator fun invoke(
        comicId: Long,
        pageNumber: Int,
        totalPages: Int = 0,
        sessionDurationSeconds: Long = 0L,
        pagesTurned: Int = 0
    ) {
        comicRepository.updateReadingProgress(comicId, pageNumber, totalPages)
        if (sessionDurationSeconds > 0 || pagesTurned > 0) {
            statsRepository.recordReadingSession(comicId, pagesTurned, sessionDurationSeconds)
        }
    }
}

