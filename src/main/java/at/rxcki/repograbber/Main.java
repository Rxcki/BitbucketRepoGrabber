package at.rxcki.repograbber;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class.getClassLoader());

    private static final File PROJECTS = new File("Projects");
    private static Properties properties;

    public static void main(String[] args) throws IOException, InterruptedException {
        loadProperties();
        if (properties == null)
            return; //Can't start without the config

        if (!PROJECTS.exists()) {
            PROJECTS.mkdir();
            LOGGER.info("Created empty Projects directory");
        }
        if (PROJECTS.listFiles().length > 0) {
            LOGGER.warn("Projects directory is not empty! Stopping to avoid overwrite");
            return;
        }

        String projectsRaw = get(properties.get("url")+"/rest/api/1.0/projects");
        LOGGER.info(projectsRaw);

        Document projects = Document.parse(projectsRaw);
        for (Document project : ((List<Document>) projects.get("values"))) {
            LOGGER.info(project.get("key"));
            File projectFile = new File(PROJECTS.getAbsolutePath() + "/" + project.get("name"));
            projectFile.mkdir();

            String projDetailRaw = get(properties.get("url")+"/rest/api/1.0/projects/" + project.get("key") + "/repos");
            Document repositories = Document.parse(projDetailRaw);
            for (Document repo : ((List<Document>) repositories.get("values"))) {
                File repoFile = new File(projectFile.getAbsolutePath() + "/" + repo.get("name"));
                repoFile.mkdir();

                String cloneUrl = (String) ((List<Document>) ((Document) repo.get("links")).get("clone")).get(0).get("href");
                LOGGER.info(cloneUrl);

                ProcessBuilder builder = new ProcessBuilder("git", "clone", cloneUrl, repoFile.getAbsolutePath());
                builder.directory(repoFile);
                builder.inheritIO();
                builder.start().waitFor();

                String branchesRaw = get(properties.get("url")+"/rest/api/1.0/projects/"+project.get("key")+"/repos/"+repo.getString("slug")+"/branches");
                Document branches = Document.parse(branchesRaw);
                for (Document branch : ((List<Document>) branches.get("values"))) {
                    builder = new ProcessBuilder("git", "checkout", branch.getString("displayId"));
                    builder.directory(repoFile);
                    builder.inheritIO();
                    builder.start().waitFor();
                }
            }
        }
    }

    private static void loadProperties() throws IOException {
        File propertyFile = new File("Config.properties");
        if (!propertyFile.exists()) {
            LOGGER.warn("No config.properties found! Loading defaults and stopping...");
            FileUtils.copyInputStreamToFile(Main.class.getClassLoader().getResourceAsStream("config.properties"), propertyFile);
            return;
        }

        try (FileInputStream in = new FileInputStream(propertyFile)) {
            properties = new Properties();
            properties.load(in);
        }
        LOGGER.info("Loaded config!");
    }

    public static String get(String url) throws IOException {
        URL url1 = new URL(url);
        String authString = properties.get("username")+":"+properties.get("password");

        HttpURLConnection connection = (HttpURLConnection) url1.openConnection();
        connection.setRequestMethod("GET");
        connection.addRequestProperty("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(
                        authString.getBytes()));

        connection.connect();
        StringWriter writer = new StringWriter();
        IOUtils.copy(connection.getInputStream(), writer, connection.getContentEncoding());
        String theString = writer.toString();
        return theString;
    }
}
