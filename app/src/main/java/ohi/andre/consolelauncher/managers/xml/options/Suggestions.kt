package ohi.andre.consolelauncher.managers.xml.options

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

        override fun info(): String? {
            return "If false, suggestions won't be shown"
        }
    },
    transparent_suggestions {
        override fun defaultValue(): String? {
            return "false"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, the background will be transparent"
        }
    },
    default_text_color {
        override fun defaultValue(): String? {
            return "#000000"
        }

        override fun info(): String? {
            return "The default text color"
        }
    },
    default_background_color {
        override fun defaultValue(): String? {
            return "#ffffff"
        }

        override fun info(): String? {
            return "The default background color"
        }
    },
    apps_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "Apps suggestions text color"
        }
    },
    apps_background_color {
        override fun defaultValue(): String? {
            return "#00897B"
        }

        override fun info(): String? {
            return "Apps suggestions background color"
        }
    },
    alias_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "Aliases suggestions text color"
        }
    },
    alias_background_color {
        override fun defaultValue(): String? {
            return "#FF5722"
        }

        override fun info(): String? {
            return "Aliases suggestions background color"
        }
    },
    cmd_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "Commands suggestions text color"
        }
    },
    cmd_background_color {
        override fun defaultValue(): String? {
            return "#76FF03"
        }

        override fun info(): String? {
            return "Commands suggestions background color"
        }
    },
    song_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "Songs suggestions text color"
        }
    },
    song_background_color {
        override fun defaultValue(): String? {
            return "#EEFF41"
        }

        override fun info(): String? {
            return "Songs suggestions background color"
        }
    },
    contact_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "Contacts suggestions text color"
        }
    },
    contact_background_color {
        override fun defaultValue(): String? {
            return "#64FFDA"
        }

        override fun info(): String? {
            return "Contacts suggestions background color"
        }
    },
    file_text_color {
        override fun defaultValue(): String? {
            return ""
        }

        override fun info(): String? {
            return "Files suggestions text color"
        }
    },
    file_background_color {
        override fun defaultValue(): String? {
            return "#03A9F4"
        }

        override fun info(): String? {
            return "Files suggestions background color"
        }
    },
    suggest_alias_default {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, your alias will be shown when the input field is empty"
        }
    },
    suggest_appgp_default {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, your app groups will be shown when the input field is empty"
        }
    },
    click_to_launch {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.BOOLEAN
        }

        override fun info(): String? {
            return "If true, some suggestions will be executed as soon as you click them"
        }
    },
    suggestions_size {
        override fun defaultValue(): String? {
            return "12"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The text size of the suggestions"
        }
    },
    double_space_click_first_suggestion {
        override fun defaultValue(): String? {
            return "true"
        }

        override fun type(): String? {
            return XMLPrefsSave.Companion.BOOLEAN
        }

        override fun info(): String? {
            return "If true, Re:T-UI will simulate a click on the current first suggestion if you double-click the space bar"
        }
    },
    noinput_suggestions_order {
        override fun defaultValue(): String? {
            return "0(5)1(5)2(2)3(5)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The order and the number of suggestions that appears on-screen when the input field is empty. 0=apps, 1=alias, 2=cmds, 3=app groups. Put between round brackets the maximum number of suggestions of the leading type"
        }
    },
    suggestions_order {
        override fun defaultValue(): String? {
            return "2(2)0(5)1(5)3(3)"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The order and the number of suggestions that appears on-screen. 0=apps, 1=alias, 2=cmds, 3=app groups. Put between round brackets the maximum number of suggestions of the leading type"
        }
    },
    noinput_min_command_priority {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The minimum priority of a command shown when the input field is empty"
        }
    },
    suggestions_per_category {
        override fun defaultValue(): String? {
            return "5"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The number of suggestions shown per category. This doesn\'t affect \"noinput_suggestions_order\" and \"suggestions_order\""
        }
    },
    suggestions_deadline {
        override fun defaultValue(): String? {
            return "0.45"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "The min/max rank that a suggestion needs to get in order to be shown. min/max depends on the comparison algorithm"
        }
    },
    suggestions_algorithm {
        override fun defaultValue(): String? {
            return "13"
        }

        override fun type(): String? {
            return XMLPrefsSave.INTEGER
        }

        override fun info(): String? {
            return "The algorithm used to compare strings"
        }
    },
    suggestions_quickcompare_n {
        override fun defaultValue(): String? {
            return "3"
        }

        override fun type(): String? {
            return XMLPrefsSave.Companion.INTEGER
        }

        override fun info(): String? {
            return "If the input is shorter than n characters, Re:T-UI will try to show you the entries which start with those characters"
        }
    },
    hide_suggestions_when_empty {
        override fun defaultValue(): String? {
            return "always"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "If \"always\" the suggestion area will be hidden when there are no suggestions. If \"true\" it will be hidden only if also the input area is empty. \"false\" disables the feature"
        }
    },
    suggestions_spaces {
        override fun defaultValue(): String? {
            return "15,15,25,20"
        }

        override fun type(): String? {
            return XMLPrefsSave.TEXT
        }

        override fun info(): String? {
            return "Suggestion chip spacing in pixels: [outer horizontal margin],[outer vertical margin],[inner horizontal padding],[inner vertical padding]."
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
