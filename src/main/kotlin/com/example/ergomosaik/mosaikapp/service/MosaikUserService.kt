package com.example.ergomosaik.mosaikapp.service

import org.ergoplatform.explorer.client.model.Balance
import com.example.ergomosaik.mosaikapp.db.MosaikUser
import com.example.ergomosaik.mosaikapp.db.MosaikUserRepo
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MosaikUserService(
    val mosaikUserRepo: MosaikUserRepo,
) {
    private val cachedBalances = HashMap<String, Balance>()
    private val TOKEN_SEPARATOR = ","

    fun getMosaikUser(guid: String): MosaikUser? =
        mosaikUserRepo.findByMosaikUuid(guid).orElse(null)

    @Transactional
    fun setP2PKAddress(guid: String, p2PKAddress: String?) {
        val user = getMosaikUser(guid)
        mosaikUserRepo.save(MosaikUser(user?.id ?: 0, guid, p2PKAddress, user?.tokensList))
    }

    @Scheduled(fixedRate = 60 * 1000L)
    fun removeCache() {
        synchronized(cachedBalances) {
            cachedBalances.clear()
        }
    }

}