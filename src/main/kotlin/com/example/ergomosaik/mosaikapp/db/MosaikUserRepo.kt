package com.example.ergomosaik.mosaikapp.db

import org.springframework.data.repository.PagingAndSortingRepository
import java.util.*

interface MosaikUserRepo: PagingAndSortingRepository<MosaikUser, Long> {
    fun findByMosaikUuid(mosaikUuid: String): Optional<MosaikUser>
}