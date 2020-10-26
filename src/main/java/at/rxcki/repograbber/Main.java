package at.rxcki.repograbber;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Base64;
import java.util.List;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger(Main.class.getClassLoader());

    static File mainFile = new File("Projects");

    public static void main(String[] args) throws IOException, InterruptedException {
        mainFile.mkdir();

        String projectsRaw = get("https://bitbucket.icevizion.de/rest/api/1.0/projects");
        LOGGER.info(projectsRaw);

        Document projects = Document.parse(projectsRaw);
        for (Document project : ((List<Document>) projects.get("values"))) {
            LOGGER.info(project.get("key"));
            File projectFile = new File(mainFile.getAbsolutePath() + "/" + project.get("name"));
            projectFile.mkdir();

            String projDetailRaw = get("https://DOMAIN/rest/api/1.0/projects/" + project.get("key") + "/repos");
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

                String branchesRaw = get("https://DOMAIN/rest/api/1.0/projects/"+project.get("key")+"/repos/"+repo.getString("slug")+"/branches");
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

    public static String get(String url) throws IOException {
        URL url1 = new URL(url);

        HttpsURLConnection connection = (HttpsURLConnection) url1.openConnection();
        connection.setRequestMethod("GET");
        connection.addRequestProperty("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(
                        "username:password".getBytes()));

        connection.connect();
        StringWriter writer = new StringWriter();
        IOUtils.copy(connection.getInputStream(), writer, connection.getContentEncoding());
        String theString = writer.toString();
        return theString;
    }
}
