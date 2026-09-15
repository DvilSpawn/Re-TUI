package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

/**
 * Created by francescoandreuzzi on 24/09/2017.
 */
enum class Suggestions : XMLPrefsSave {
    show_suggestions {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_show_suggestions_description
        }
    },
    transparent_suggestions {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_transparent_suggestions_description
        }
    },
    default_text_color {
        override fun defaultValue(): String? {
            return "#000000"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_default_text_color_description
        }
    },
    default_background_color {
        override fun defaultValue(): String? {
            return "#ffffff"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_default_background_color_description
        }
    },
    apps_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_apps_text_color_description
        }
    },
    apps_background_color {
        override fun defaultValue(): String? {
            return "#00897B"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_apps_background_color_description
        }
    },
    alias_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_alias_text_color_description
        }
    },
    alias_background_color {
        override fun defaultValue(): String? {
            return "#FF5722"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_alias_background_color_description
        }
    },
    cmd_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_cmd_text_color_description
        }
    },
    cmd_background_color {
        override fun defaultValue(): String? {
            return "#76FF03"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_cmd_background_color_description
        }
    },
    song_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_song_text_color_description
        }
    },
    song_background_color {
        override fun defaultValue(): String? {
            return "#EEFF41"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_song_background_color_description
        }
    },
    contact_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_contact_text_color_description
        }
    },
    contact_background_color {
        override fun defaultValue(): String? {
            return "#64FFDA"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_contact_background_color_description
        }
    },
    file_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_file_text_color_description
        }
    },
    file_background_color {
        override fun defaultValue(): String? {
            return "#03A9F4"
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_file_background_color_description
        }
    },
    suggest_alias_default {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggest_alias_default_description
        }
    },
    suggest_appgp_default {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggest_appgp_default_description
        }
    },
    click_to_launch {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_click_to_launch_description
        }
    },
    suggestions_size {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggestions_size_description
        }
    },
    double_space_click_first_suggestion {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.Companion.BOOLEAN
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_double_space_click_first_suggestion_description
        }
    },
    noinput_suggestions_order {
        override fun defaultValue(): String? {
            return "0(5)1(5)2(2)3(5)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_noinput_suggestions_order_description
        }
    },
    suggestions_order {
        override fun defaultValue(): String? {
            return "2(2)0(5)1(5)3(3)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggestions_order_description
        }
    },
    noinput_min_command_priority {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_noinput_min_command_priority_description
        }
    },
    suggestions_per_category {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggestions_per_category_description
        }
    },
    suggestions_deadline {
        override fun defaultValue(): String? {
            return "0.45"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggestions_deadline_description
        }
    },
    suggestions_algorithm {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggestions_algorithm_description
        }
    },
    suggestions_quickcompare_n {
        override fun defaultValue(): String? {
            return "3"
        }

        override fun type(): String? {
            return XMLPrefsSave.Companion.INTEGER
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggestions_quickcompare_n_description
        }
    },
    hide_suggestions_when_empty {
        override fun defaultValue(): String? {
            return "always"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_hide_suggestions_when_empty_description
        }
    },
    suggestions_spaces {
        override fun defaultValue(): String? {
            return "15,15,25,20"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun infoRes(): Int {
            return R.string.setting_suggestions_suggestions_spaces_description
        }
    };

    override fun parent(): XMLPrefsElement? {
        return XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS
    }

    override fun label(): String? {
        return name
    }

    override fun type(): String? {
        return XMLPrefsSave.COLOR
    }

    override fun invalidValues(): Array<String?>? {
        return null
    }

    override fun getLowercaseString(): String? {
        return label()
    }

    override fun getString(): String? {
        return label()
    }
}
