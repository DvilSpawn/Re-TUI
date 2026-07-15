package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell
import java.util.Locale
import ohi.andre.consolelauncher.managers.music.Song

class music : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        next {
            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) {
                    execute("NEXT")
                    return null
                }

                pack.player!!.useLocalSongs()
                val title = pack.player!!.playNext()
                if (title != null) return pack.context.getString(R.string.output_playing) + Tuils.SPACE + title
                return null
            }
        },
        previous {
            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) {
                    execute("PREVIOUS")
                    return null
                }

                pack.player!!.useLocalSongs()
                val title = pack.player!!.playPrev()
                if (title != null) return pack.context.getString(R.string.output_playing) + Tuils.SPACE + title
                return null
            }
        },
        ls {
            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) return pack.context.getString(R.string.output_musicdisabled)

                return pack.player!!.lsSongs()
            }
        },
        play {
            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) {
                    execute("PLAY_PAUSE")
                    return null
                }

                pack.player!!.useLocalSongs()
                val title = pack.player!!.play()
                if (title == null) return null
                return pack.context.getString(R.string.output_playing) + Tuils.SPACE + title
            }
        },
        stop {
            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) {
                    execute("CLOSE")
                    return null
                }

                pack.player!!.stop()
                return null
            }
        },
        select {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.SONG)
            }

            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) return pack.context.getString(R.string.output_musicdisabled)

                val s = pack.getString()
                pack.player!!.useLocalSongs()
                pack.player!!.select(s)
                return null
            }

            override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? {
                return pack.context.getString(R.string.output_songnotfound)
            }
        },
        info {
            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) return pack.context.getString(R.string.output_musicdisabled)

                val builder = StringBuilder()

                val m: MusicManager2 = pack.player!!
                val song = m.get(m.songIndex)
                if (song == null) return pack.context.getString(R.string.output_songnotfound)

                builder.append("Name: ").append(song.getTitle()).append(Tuils.NEWLINE)
                if (song.getID() == -1L) builder.append("Path: ").append(song.getPath())
                    .append(Tuils.NEWLINE)
                builder.append(Tuils.NEWLINE)

                var curS = m.getCurrentPosition() / 1000
                var curMin = 0
                if (curS >= 60) {
                    curMin = curS / 60
                    curS = curS % 60
                }

                var s = m.getDuration() / 1000
                var min = 0
                if (s >= 60) {
                    min = s / 60
                    s = s % 60
                }

                builder.append(if (curMin > 0) curMin.toString() + "." + curS else curS.toString() + "s")
                    .append(" of ")
                    .append(if (min > 0) min.toString() + "." + s else s.toString() + "s")
                    .append(" (").append(
                        Tuils.percentage(
                            m.getCurrentPosition().toDouble(),
                            m.getDuration().toDouble()
                        )
                    ).append("%)")
                return builder.toString()
            }
        },
        seekto {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                if ((pack as MainPack).player == null) return pack.context.getString(R.string.output_musicdisabled)

                pack.player!!.seekTo(pack.getInt() * 1000)
                return null
            }

            override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? {
                return pack.context.getString(R.string.invalid_integer)
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? {
            return null
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_music)
        }

        override fun args(): IntArray? {
            return IntArray(0)
        }

        companion object {
            fun get(p: String): Param? {
                var p = p
                p = p.lowercase(Locale.getDefault())
                val ps = entries.toTypedArray()
                for (p1 in ps) if (p.endsWith(p1.label()!!)) return p1
                return null
            }

            fun labels(): Array<String?> {
                val ps = entries.toTypedArray()
                val ss = arrayOfNulls<String>(ps.size)

                for (count in ps.indices) {
                    ss[count] = ps[count].label()
                }

                return ss
            }
        }
    }

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return Param.Companion.get(param)
    }

    override fun priority(): Int {
        return 4
    }

    override fun helpRes(): Int {
        return R.string.help_music
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun doThings(pack: ExecutePack): String? {
        return null
    }

    companion object {
        private fun execute(code: String?) {
            Shell.SH.run("input keyevent KEYCODE_MEDIA_" + code)
        }
    }
}
