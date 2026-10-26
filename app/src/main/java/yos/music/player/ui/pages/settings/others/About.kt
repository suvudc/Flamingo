package yos.music.player.ui.pages.settings.others

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import yos.music.player.BuildConfig
import yos.music.player.R
import yos.music.player.ui.pages.settings.DefaultItem
import yos.music.player.ui.pages.settings.GroupSpacer
import yos.music.player.ui.pages.settings.LabelItem
import yos.music.player.ui.pages.settings.ListHeader
import yos.music.player.ui.pages.settings.SettingBackground
import yos.music.player.ui.pages.settings.startWeb
import yos.music.player.ui.widgets.basic.RoundColumn
import yos.music.player.ui.widgets.basic.Title

@Composable
fun About(navController: NavController) =
    SettingBackground {
        Title(title = stringResource(id = R.string.settings_others_about),
            onBack = {
                navController.popBackStack()
            },
            content = {
                item("settings") {
                    Column(Modifier.fillMaxSize()) {
                        val context = LocalContext.current

                        ListHeader(content = stringResource(id = R.string.settings_others_about_info))

                        RoundColumn {
                            DefaultItem(
                                title = stringResource(id = R.string.app_name),
                                onClick = null,
                                desc = "${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}"
                            )
                        }

                        GroupSpacer()

                        ListHeader(content = stringResource(id = R.string.settings_others_about_developers))
                        RoundColumn {
                            LabelItem(
                                title = "Suvu (@suvudc)",
                                desc = "Main developer · Music fork"
                            ) {
                                startWeb(
                                    url = "https://github.com/suvudc",
                                    context
                                )
                            }
                        }

                        GroupSpacer()

                        RoundColumn {
                            LabelItem(
                                title = "Music by Suvu on GitHub",
                                desc = "Source code and project updates",
                                superLink = true
                            ) {
                                startWeb("https://github.com/suvudc/Flamingo/tree/fix/pixel-media-artwork", context)
                            }
                        }

                        GroupSpacer()
                        ListHeader(content = "Open-source credits")
                        RoundColumn {
                            LabelItem(
                                title = "Flamingo · Yos-X / MULTIPLY STUDIO",
                                desc = "Original project · GNU GPL v3"
                            ) {
                                startWeb("https://github.com/suvudc/Flamingo/blob/fix/pixel-media-artwork/LICENSE", context)
                            }
                        }
                    }
                }
            }
        )
    }
