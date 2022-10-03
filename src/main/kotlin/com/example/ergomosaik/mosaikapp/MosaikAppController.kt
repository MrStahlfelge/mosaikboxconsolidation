package com.example.ergomosaik.mosaikapp

import org.ergoplatform.mosaik.*
import com.example.ergomosaik.mosaikapp.service.ExplorerApiService
import com.example.ergomosaik.mosaikapp.service.MosaikUserService
import org.ergoplatform.mosaik.jackson.MosaikSerializer
import org.ergoplatform.mosaik.model.MosaikApp
import org.ergoplatform.mosaik.model.MosaikManifest
import org.ergoplatform.mosaik.model.ui.layout.HAlignment
import org.ergoplatform.mosaik.model.ui.layout.Padding
import org.ergoplatform.mosaik.model.ui.layout.VAlignment
import org.ergoplatform.mosaik.model.ui.text.LabelStyle
import org.ergoplatform.wallet.Constants
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.*
import javax.servlet.http.HttpServletRequest

@RestController
@CrossOrigin
class MosaikAppController(
    private val mosaikUserService: MosaikUserService,
    private val explorerApiService: ExplorerApiService,
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
                        // TODO unconfirmed tx
                        val mainnet = !p2PKAddress.startsWith("3")

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

                            val yearsCent =
                                ((currentBlock - oldestCreationHeight) * 10 / org.ergoplatform.wallet.protocol.Constants.BlocksPerYear())

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
                                } else if (unspentBoxInfo.total <= 40) {
                                    "Consider consolidation."
                                } else {
                                    "You should consolidate."
                                }) + "\nStorage rent will be applied on boxes older than 4 years.",
                                textAlignment = HAlignment.CENTER
                            )
                        }

                    }
                }
            }

        }
    }

    companion object {
        const val MAIN_APP_URI = "/"
    }
}