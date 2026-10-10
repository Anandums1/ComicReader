package com.comicreader.domain.usecase

import com.comicreader.domain.model.Comic
import com.comicreader.domain.model.ComicFormat
import com.comicreader.domain.repository.ComicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchComicsUseCase @Inject constructor(
    private val comicRepository: ComicRepository
) {
    operator fun invoke(query: String, formatFilter: ComicFormat? = null): Flow<List<Comic>> {
        return comicRepository.searchComics(query, formatFilter?.extension)
    }
}

