package com.example.ergomosaik.mosaikapp.service

import okhttp3.OkHttpClient
import org.ergoplatform.appkit.RestApiErgoClient
import org.ergoplatform.explorer.client.DefaultApi
import org.springframework.stereotype.Service
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service
class ExplorerApiService(private val okHttpClient: OkHttpClient) {
    val timeout = 30L // 30 seconds since Explorer can be slooooow

    private val api by lazy {
        buildExplorerApi(RestApiErgoClient.defaultMainnetExplorerUrl)
    }

    private val testapi by lazy {
        buildExplorerApi(RestApiErgoClient.defaultTestnetExplorerUrl)
    }

    private fun buildExplorerApi(url: String) = Retrofit.Builder()
        .baseUrl(url)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            okHttpClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS).build()
        )
        .build()
        .create(DefaultApi::class.java)

    private fun <T> wrapCall(call: () -> Call<T>): T {
        val explorerCall = call().execute()

        if (!explorerCall.isSuccessful)
            throw IOException("Error calling Explorer: ${explorerCall.errorBody()}")

        return explorerCall.body()!!
    }

    fun getBoxesByAddress(
        mainnet: Boolean,
        address: String,
        offset: Int,
        limit: Int,
        ascending: Boolean
    ) =
        wrapCall {
            (if (mainnet) api else testapi)
                .getApiV1BoxesUnspentByaddressP1(
                    address,
                    offset,
                    limit,
                    if (ascending) "asc" else "desc"
                )
        }

    private var blockHeightUpdateTs: Long = 0
    private var blockHeightVal: Long = 0

    fun getCurrentBlockHeight(mainnet: Boolean): Long {

        if (System.currentTimeMillis() - blockHeightUpdateTs > 5 * 60 * 1000L)
            try {
                blockHeightVal = wrapCall {
                    (if (mainnet) api else testapi).getApiV1Blocks(0, 1, "height", "desc")
                }.total.toLong()
                blockHeightUpdateTs = System.currentTimeMillis()
            } catch (t: Throwable) {
                // ignore error, use cached value
            }

        return blockHeightVal
    }

    fun getMempoolTx(mainnet: Boolean, address: String, offset: Int, limit: Int) =
        wrapCall {
            (if (mainnet) api else testapi).getApiV1MempoolTransactionsByaddressP1(
                address,
                offset,
                limit
            )
        }.items
}
