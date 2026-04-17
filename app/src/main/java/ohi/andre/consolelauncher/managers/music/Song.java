package ohi.andre.consolelauncher.managers.music;

import java.io.File;

import it.andreuzzi.comparestring2.StringableObject;

/**
 * Created by francescoandreuzzi on 17/08/2017.
 */

public class Song implements StringableObject  {

    private long id;
    private String title, path, lowercaseTitle, singer;

    public Song(long songID, String songTitle, String singer) {
        id = songID;
        title = songTitle;
        this.singer = singer;
        this.lowercaseTitle = title.toLowerCase();
    }

    public Song(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf(".");
        if (dot != -1) {
            name = name.substring(0, dot);
        }

        this.title = name;
        this.path = file.getAbsolutePath();
        this.id = -1;
        this.singer = "Unknown";
        this.lowercaseTitle = title.toLowerCase();
    }

    public long getID() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSinger() {
        return singer;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String getLowercaseString() {
        return lowercaseTitle;
    }

    @Override
    public String getString() {
        return title;
    }
}
