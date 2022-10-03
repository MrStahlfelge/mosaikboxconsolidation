package com.example.ergomosaik.mosaikapp

import com.example.ergomosaik.mosaikapp.service.ExplorerApiService
import com.example.ergomosaik.mosaikapp.service.MosaikUserService
import com.example.ergomosaik.mosaikapp.service.PeerService
import org.ergoplatform.appkit.Address
import org.ergoplatform.appkit.ErgoToken
import org.ergoplatform.appkit.Parameters
import org.ergoplatform.ergopay.ErgoPayResponse
import org.ergoplatform.mosaik.*
import org.ergoplatform.mosaik.jackson.MosaikSerializer
import org.ergoplatform.mosaik.model.MosaikApp
import org.ergoplatform.mosaik.model.MosaikManifest
import org.ergoplatform.mosaik.model.ui.ForegroundColor
import org.ergoplatform.mosaik.model.ui.layout.HAlignment
import org.ergoplatform.mosaik.model.ui.layout.Padding
import org.ergoplatform.mosaik.model.ui.text.LabelStyle
import org.ergoplatform.wallet.protocol.Constants
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.lang.Integer.min
import java.lang.Long.max
import java.util.*
import javax.servlet.http.HttpServletRequest

@RestController
@CrossOrigin
class MosaikAppController(
    private val mosaikUserService: MosaikUserService,
    private val explorerApiService: ExplorerApiService,
    private val peerService: PeerService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping(MAIN_APP_URI)
    fun mainApp(
        @RequestHeader headers: Map<String, String>,
        httpServletRequest: HttpServletRequest
    ): MosaikApp {
        return mosaikApp(
            "Box Consolidation",
            1,
            "Check number of boxes and consolidate when necessary",
            targetCanvasDimension = MosaikManifest.CanvasDimension.COMPACT_WIDTH,
        ) {
            val context = MosaikSerializer.fromContextHeadersMap(headers)

            reloadApp { id = RELOAD_ACTION_ID }

            val p2PKAddress = mosaikUserService.getMosaikUser(context.guid)?.p2PKAddress

            val connectWalletRequest =
                backendRequest(getHostUrl(httpServletRequest) + URL_CONNECT_WALLET)

            card(Padding.HALF_DEFAULT) {
                column(Padding.DEFAULT, spacing = Padding.DEFAULT) {
                    ergoAddressChooser(
                        ID_SELECT_WALLET,

                        ) {
                        onValueChangedAction = connectWalletRequest.id
                        value = p2PKAddress
                    }

                    if (p2PKAddress == null) {
                        label(
                            "Connect your wallet to check available boxes",
                            style = LabelStyle.HEADLINE2,
                            textAlignment = HAlignment.CENTER
                        )
                    }

                    p2PKAddress?.let {

                        val mainnet = !p2PKAddress.startsWith("3")

                        val hasUnconfirmedTx =
                            explorerApiService.getMempoolTx(mainnet, p2PKAddress, 0, 1).isNotEmpty()

                        if (hasUnconfirmedTx) {
                            label(
                                "This address has unconfirmed transactions. The information displayed below does not reflect this. Please check again later.",
                                style = LabelStyle.BODY1BOLD,
                                textAlignment = HAlignment.CENTER,
                                textColor = ForegroundColor.PRIMARY
                            )
                        }

                        val unspentBoxInfo =
                            try {
                                explorerApiService.getBoxesByAddress(
                                    mainnet,
                                    p2PKAddress,
                                    0,
                                    20,
                                    true
                                )
                            } catch (t: Throwable) {
                                null
                            }

                        hr(Padding.HALF_DEFAULT)

                        if (unspentBoxInfo == null) {
                            label(
                                "Problems reaching Ergo network. Please try again later.",
                                style = LabelStyle.HEADLINE2,
                                textAlignment = HAlignment.CENTER
                            )
                        } else if (unspentBoxInfo.total == 0) {
                            label(
                                "No unspent boxes on this address.",
                                style = LabelStyle.HEADLINE2,
                                textAlignment = HAlignment.CENTER
                            )
                        } else {
                            column(Padding.DEFAULT) {
                                label(unspentBoxInfo.total.toString(), LabelStyle.HEADLINE1)

                                label("unspent boxes", LabelStyle.HEADLINE2)
                            }

                            label(
                                if (unspentBoxInfo.total <= 100) {
                                    "Less than 100 boxes - that is fine."
                                } else if (unspentBoxInfo.total <= 300) {
                                    "Consider consolidation."
                                } else {
                                    "You should consolidate."
                                },
                                textAlignment = HAlignment.CENTER
                            )

                            hr(Padding.HALF_DEFAULT)

                            val oldestCreationHeight =
                                unspentBoxInfo.items.minOf { it.creationHeight }
                            val currentBlock = explorerApiService.getCurrentBlockHeight(mainnet)

                            val yearsCent = max(
                                0,
                                ((currentBlock - oldestCreationHeight) * 10 / Constants.BlocksPerYear())
                            )

                            column(Padding.DEFAULT) {
                                label("Oldest box age", LabelStyle.HEADLINE2)

                                label(
                                    "%,.1f".format(Locale.US, yearsCent / 10.0) + " years",
                                    LabelStyle.HEADLINE1
                                )

                            }
                            label(
                                (if (yearsCent <= 30) {
                                    "You are fine."
                                } else if (unspentBoxInfo.total <= 35) {
                                    "Consider consolidation."
                                } else {
                                    "You should consolidate."
                                }) + "\nStorage rent will be applied on boxes older than 4 years.",
                                textAlignment = HAlignment.CENTER
                            )

                            val shouldConsolidate = unspentBoxInfo.total > 100 || yearsCent > 30

                            if (shouldConsolidate) {
                                hr(Padding.HALF_DEFAULT)

                                button(
                                    "Consolidate ${
                                        min(100, unspentBoxInfo.total)
                                    } boxes"
                                ) {
                                    onClickAction = addAction(
                                        invokeErgoPay(
                                            "ergopay://" + getHostUrl(httpServletRequest).substringAfter(
                                                "://"
                                            ) + ERGOPAY_URI + p2PKAddress,
                                            "epConsolidate"
                                        )
                                    ).id
                                }
                            }
                        }

                    }
                }
            }

        }
    }

    @GetMapping("$ERGOPAY_URI{p2pkaddress}")
    fun epConsolidate(@PathVariable p2pkaddress: String): ErgoPayResponse {
        val response = ErgoPayResponse()
        try {
            val ergoAddress = Address.create(p2pkaddress)
            val ergoClient = peerService.getErgoClient(ergoAddress.isMainnet)
            val (boxnum, reduced) = ergoClient.execute { ctx ->
                val unspentBoxes = ctx.dataSource.getUnspentBoxesFor(ergoAddress, 0, 100)
                val fee = (unspentBoxes.size / 50) * Parameters.MinFee

                val txB = ctx.newTxBuilder()

                var completeValue = 0L
                val tokenMap: HashMap<String, Long> = HashMap()
                unspentBoxes.forEach { input ->
                    completeValue += input.value
                    input.tokens.forEach { token ->
                        val tokenIdString = token.id.toString()
                        val et = tokenMap[tokenIdString] ?: 0L
                        tokenMap[tokenIdString] = et + token.value
                    }
                }
                val tokenList = tokenMap.map { ErgoToken(it.key, it.value) }

                val outBoxBuilder = txB.outBoxBuilder()
                    .contract(ergoAddress.toErgoContract())
                    .value(completeValue - fee)

                if (tokenList.isNotEmpty())
                    outBoxBuilder.tokens(*tokenList.toTypedArray())

                val outBox = outBoxBuilder.build()

                val tx = txB.boxesToSpend(unspentBoxes)
                    .fee(fee)
                    .outputs(outBox)
                    .sendChangeTo(ergoAddress.ergoAddress)
                    .build()

                Pair(unspentBoxes.size, ctx.newProverBuilder().build().reduce(tx, 0).toBytes())
            }
            response.reducedTx = Base64.getUrlEncoder().encodeToString(reduced)
            response.address = p2pkaddress
            response.message =
                "Please sign the transaction to consolidate $boxnum boxes."
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION
        } catch (t: Throwable) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR
            response.message = t.message
        }
        return response
    }

    companion object {
        const val MAIN_APP_URI = "/"
        const val ERGOPAY_URI = "/consolidate/"
    }
}