package org.ninthworld.hdspritesrepacker;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.json.*;

public class HDSpritesRepacker {

    public static final String jarName = "HDSpritesRepacker.jar";

    public static final String imageResizerPath = "ImageResizer-r133.exe";
    public static void imageResizerShell(String inImage, String outImage) throws IOException {
        Runtime.getRuntime().exec(String.format("\"%s\" /load \"%s\" /resize auto \"XBR 2x\" /save \"%s\"", imageResizerPath, inImage, outImage));
    }

    public static void main(String[] args) {
        // Check for 2 arguments
        if (args.length < 2) {
            System.out.println(String.format("Usage: java -jar %s [ModFolder] [RepackedFolder]", jarName));
            System.exit(0); return;
        }

        // Check for existence of ImageResizer executable
        File imageResizer = new File(imageResizerPath);
        if (!imageResizer.exists()) {
            System.out.println(String.format("Error: \"%s\" could not be found.", imageResizer.toString()));
            System.exit(-1); return;
        }

        // Check for existence of mod folder
        File modDirectory = new File(args[0]);
        if (!modDirectory.exists()) {
            System.out.println(String.format("Error: Mod folder \"%s\" could not be found or is invalid.", modDirectory.toString()));
            System.exit(-1); return;
        }

        // Check for successful creation and/or existence of repacked folder
        File repackDirectory = new File(args[1]);
        if (!repackDirectory.mkdirs() && !repackDirectory.exists()) {
            System.out.println(String.format("Error: Repacked folder \"%s\" could not be created.", repackDirectory.toString()));
            System.exit(-1); return;
        }

        // Check if the mod folder has a manifest.json
        File manifestFile = Paths.get(modDirectory.toString(), "manifest.json").toFile();
        if (!manifestFile.exists()) {
            System.out.println("Error: Could not find manifest.json in mod folder.");
            System.exit(-1); return;
        }

        // Check if the mod folder has a content.json
        File contentFile = Paths.get(modDirectory.toString(), "content.json").toFile();
        if (!contentFile.exists()) {
            System.out.println("Error: Could not find content.json in mod folder.");
            System.exit(-1); return;
        }

        // Check if the mod folder has a config.json, and if it does, copy it
        File configFile = Paths.get(modDirectory.toString(), "config.json").toFile();
        if (configFile.exists()) {
            try (InputStream is = Files.newInputStream(configFile.toPath())) {
                String json = loadJSON(is);
                JSONTokener tokener = new JSONTokener(json);
                JSONObject root = new JSONObject(tokener);

                BufferedWriter writer = Files.newBufferedWriter(Paths.get(repackDirectory.toString(), "config.json"));
                root.write(writer, 2, 0);
                writer.write("\n");
                writer.close();
            } catch (Exception e) {
                System.out.println("Error: Failed to read/write config.json.");
                e.printStackTrace();
                System.exit(-1); return;
            }
        }

        // Read manifest.json and change UniqueID and ContentPackFor
        try (InputStream is = Files.newInputStream(manifestFile.toPath())) {
            String json = loadJSON(is);
            JSONTokener tokener = new JSONTokener(json);
            JSONObject root = new JSONObject(tokener);

            root.put("UniqueID", root.getString("UniqueID") + "HD");
            root.getJSONObject("ContentPackFor").put("UniqueID", "NinthWorld.HDSprites");
            root.getJSONObject("ContentPackFor").put("MinimumVersion", "1.1.0");

            BufferedWriter writer = Files.newBufferedWriter(Paths.get(repackDirectory.toString(), "manifest.json"));
            root.write(writer, 2, 0);
            writer.write("\n");
            writer.close();
        } catch (Exception e) {
            System.out.println("Error: Failed to read/write manifest.json.");
            e.printStackTrace();
            System.exit(-1); return;
        }

        // Read content.json and change FromArea and ToArea
        try (InputStream is = Files.newInputStream(contentFile.toPath())) {
            String json = loadJSON(is);
            JSONTokener tokener = new JSONTokener(json);
            JSONObject root = new JSONObject(tokener);
            JSONArray changes = root.getJSONArray("Changes");
            for (int i = 0; i < changes.length(); ++i) {
                JSONObject obj = (JSONObject) changes.get(i);
                if (obj.has("FromArea")) {
                    JSONObject fromArea = obj.getJSONObject("FromArea");
                    fromArea.put("X", fromArea.getInt("X") * 2);
                    fromArea.put("Y", fromArea.getInt("Y") * 2);
                    fromArea.put("Width", fromArea.getInt("Width") * 2);
                    fromArea.put("Height", fromArea.getInt("Height") * 2);
                }
                if (obj.has("ToArea")) {
                    JSONObject toArea = obj.getJSONObject("ToArea");
                    toArea.put("X", toArea.getInt("X") * 2);
                    toArea.put("Y", toArea.getInt("Y") * 2);
                    toArea.put("Width", toArea.getInt("Width") * 2);
                    toArea.put("Height", toArea.getInt("Height") * 2);
                }
            }

            BufferedWriter writer = Files.newBufferedWriter(Paths.get(repackDirectory.toString(), "content.json"));
            root.write(writer, 2, 0);
            writer.write("\n");
            writer.close();
        } catch (Exception e) {
            System.out.println("Error: Failed to read/write content.json.");
            e.printStackTrace();
            System.exit(-1); return;
        }

        // Resize all PNGs from modDirectory to repackedDirectory
        List<File> allFiles = listAllFiles(modDirectory);
        for (File file : allFiles) {
            if (getFileExt(file).equalsIgnoreCase("png")) {
                String shortPath = file.toString().replace(modDirectory.toString(), "");
                File toFile = Paths.get(repackDirectory.toString(), shortPath).toFile();
                try {
                    System.out.println(String.format("Upscaling PNG \"%s\"", shortPath));
                    if (!toFile.getParentFile().mkdirs() && !toFile.getParentFile().exists()) {
                        System.out.println(String.format("Error: Failed to create path for \"%s\"", shortPath));
                        continue;
                    }
                    imageResizerShell(file.toString(), toFile.toString());
                } catch (IOException e) {
                    System.out.println(String.format("Error: Failed to resize PNG \"%s\"", shortPath));
                    e.printStackTrace();
                }
            }
        }
    }

    public static String loadJSON(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder builder = new StringBuilder();
        String line;
        while((line = br.readLine()) != null) builder.append(line).append("\n");
        br.close();
        return builder.toString().replaceAll("//.*\\n", "\n").replaceAll("(?s)/\\*.*?\\*/", "");
    }

    public static List<File> listAllFiles(File dir) {
        List<File> allFiles = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null || files.length < 1) return allFiles;
        for (File file : files) {
            if (file.isDirectory()) allFiles.addAll(listAllFiles(file));
            else allFiles.add(file);
        }
        return allFiles;
    }

    public static String getFileExt(File file) {
        String name = file.getName();
        int index = name.lastIndexOf('.');
        if (index >= 0) return name.substring(index + 1);
        return "";
    }
}
