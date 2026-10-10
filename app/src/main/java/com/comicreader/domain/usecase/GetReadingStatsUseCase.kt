package com.comicreader.domain.usecase

import com.comicreader.domain.model.ReadingStats
import com.comicreader.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetReadingStatsUseCase @Inject constructor(
    private val statsRepository: StatsRepository
) {
    operator fun invoke(): Flow<ReadingStats> {
        return statsRepository.getReadingStats()
    }
}

