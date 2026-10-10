package com.comicreader.domain.usecase

import com.comicreader.domain.model.ComicPage
import com.comicreader.domain.repository.ComicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetComicPagesUseCase @Inject constructor(
    private val comicRepository: ComicRepository
) {
    operator fun invoke(comicId: Long): Flow<List<ComicPage>> = flow {
        val comic = comicRepository.getComicByIdOnce(comicId)
        if (comic != null && comic.pageCount > 0) {
            val pages = (0 until comic.pageCount).map { index ->
                ComicPage(pageNumber = index)
            }
            emit(pages)
        } else {
            emit(emptyList())
        }
    }
}

