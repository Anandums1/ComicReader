package com.comicreader.domain.usecase

import com.comicreader.domain.repository.ComicRepository
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val comicRepository: ComicRepository
) {
    suspend operator fun invoke(comicId: Long, isFavorite: Boolean) {
        comicRepository.toggleFavorite(comicId, isFavorite)
    }
}

