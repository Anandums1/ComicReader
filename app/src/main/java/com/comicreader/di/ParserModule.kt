package com.comicreader.di

import com.comicreader.data.scanner.ComicFileScanner
import com.comicreader.data.scanner.ComicFileScannerImpl
import com.comicreader.format.CbrParser
import com.comicreader.format.CbzParser
import com.comicreader.format.ComicParserFactory
import com.comicreader.format.EpubParser
import com.comicreader.format.PdfParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @Singleton
    abstract fun bindComicFileScanner(
        comicFileScannerImpl: ComicFileScannerImpl
    ): ComicFileScanner

    companion object {
        @Provides
        @Singleton
        fun provideCbzParser(): CbzParser = CbzParser()

        @Provides
        @Singleton
        fun provideCbrParser(): CbrParser = CbrParser()

        @Provides
        @Singleton
        fun providePdfParser(): PdfParser = PdfParser()

        @Provides
        @Singleton
        fun provideEpubParser(): EpubParser = EpubParser()

        @Provides
        @Singleton
        fun provideComicParserFactory(
            cbzParser: CbzParser,
            cbrParser: CbrParser,
            pdfParser: PdfParser,
            epubParser: EpubParser
        ): ComicParserFactory = ComicParserFactory(cbzParser, cbrParser, pdfParser, epubParser)
    }
}
