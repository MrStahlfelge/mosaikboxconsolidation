package com.example.ergomosaik.mosaikapp.service

import org.ergoplatform.appkit.ErgoClient
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.RestApiErgoClient
import org.springframework.stereotype.Service

@Service
class PeerService {
    fun getErgoClient(mainnet: Boolean = true): ErgoClient {
        return RestApiErgoClient.create(
            if (mainnet) "http://213.239.193.208:9053/" else "http://213.239.193.208:9052/",
            if (mainnet) NetworkType.MAINNET else NetworkType.TESTNET,
            "",
            if (mainnet) RestApiErgoClient.defaultMainnetExplorerUrl else RestApiErgoClient.defaultTestnetExplorerUrl,
        )
    }
}