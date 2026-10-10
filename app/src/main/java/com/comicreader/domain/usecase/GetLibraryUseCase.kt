package com.comicreader.domain.usecase

import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.model.SortOrder
import com.comicreader.domain.repository.ComicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLibraryUseCase @Inject constructor(
    private val comicRepository: ComicRepository
) {
    operator fun invoke(
        sortOrder: SortOrder = SortOrder.TITLE,
        formatFilter: ComicFormat? = null,
        query: String? = null,
        onlyFavorites: Boolean = false
    ): Flow<List<Comic>> {
        return if (formatFilter != null || !query.isNullOrBlank() || onlyFavorites) {
            comicRepository.filterComics(
                query = query,
                format = formatFilter,
                onlyFavorites = onlyFavorites,
                sortOrder = sortOrder
            )
        } else {
            comicRepository.getAllComics(sortOrder)
        }
    }
}

