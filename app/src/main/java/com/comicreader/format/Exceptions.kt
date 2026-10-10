package com.comicreader.format

class UnsupportedRarV5Exception(
    message: String = "RAR5 archives are not supported by Junrar. Please re-compress as CBZ or RAR4."
) : Exception(message)

class CorruptArchiveException(
    message: String = "The comic archive is corrupt or unreadable."
) : Exception(message)

class UnsupportedFormatException(
    message: String = "The specified file format is not supported."
) : Exception(message)

class InvalidEpubException(
    message: String = "Invalid EPUB document."
) : Exception(message)
