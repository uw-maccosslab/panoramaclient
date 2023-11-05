package edu.maccosslab.panoramaclient;

import org.apache.http.client.utils.URLEncodedUtils;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.PostCommand;
import org.labkey.remoteapi.query.Filter;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientActionImportSkyDoc extends ClientAction<ActionOptions.ImportSkyDoc>
{
    @Override
    public boolean doAction(ActionOptions.ImportSkyDoc options) throws ClientException
    {
        String panoramaFolderUrl = options.getPanoramaFolderUrl();
        String skyDocPathLocal = options.getSkyDocPathLocal();
        String skyDocNameRemote = options.getSkyDocNameRemote();

        if(panoramaFolderUrl == null || panoramaFolderUrl.trim().length() == 0)
        {
            throw new ClientException("URL string cannot be empty");
        }

        LabKeyUrlParts labKeyUrlParts = URLHelper.buildLabKeyUrlParts(panoramaFolderUrl);
        Connection connection = getConnection(labKeyUrlParts.getServerUrl(), options.getApiKey());

        WebdavUrlParts webdavUrlParts = null;
        if (options.getWebdavUrl() != null)
        {
            webdavUrlParts = URLHelper.buildWebdavUrlParts(options.getWebdavUrl());
            if (!labKeyUrlParts.getServerUrl().equals(webdavUrlParts.getServerUrl()))
            {
                throw new ClientException("Server in the Panorama folder URL and the WebDAV URL are not the same"
                        + ". Server in the Panorama folder URL: " + labKeyUrlParts.getServerUrl()
                        + ". Server in the WebDAV URL: " + webdavUrlParts.getServerUrl());
            }
            if (!labKeyUrlParts.getContainerPath().equals(webdavUrlParts.getContainerPath()))
            {
                throw new ClientException("Folder path in the Panorama folder URL and the WebDAV URL are not the same"
                        + ". Folder path in the Panorama folder URL: " + labKeyUrlParts.getContainerPath()
                        + ". Folder path in the WebDAV URL: " + webdavUrlParts.getContainerPath());
            }
        }
        if (skyDocPathLocal != null)
        {
            uploadAndImport(labKeyUrlParts, skyDocPathLocal, webdavUrlParts, connection);
        }
        else if (skyDocNameRemote != null)
        {
            importDocumentOnServer(labKeyUrlParts, skyDocNameRemote.trim(), webdavUrlParts, connection);
        }
        else
        {
            throw new ClientException("Incomplete arguments. Path to a local Skyline document or a document in the Panorama folder is required.");
        }
        return true;
    }

    private void uploadAndImport(LabKeyUrlParts labKeyUrlParts, String skyZipPath, WebdavUrlParts webdavUrlParts, Connection connection) throws ClientException
    {
        File file = new File(skyZipPath);
        if (!file.exists())
        {
            throw new ClientException("Source file does not exist: " + skyZipPath);
        }
        if (!file.isFile())
        {
            throw new ClientException("Not a file " + skyZipPath);
        }
        if (!skyZipPath.toLowerCase().endsWith(".sky.zip"))
        {
            throw new ClientException("Not a Skyline shared zip file " + file.getName());
        }

        String serverUri = labKeyUrlParts.getServerUrl();
        String containerPath = labKeyUrlParts.getContainerPath();
        LOG.info("Starting upload and import of Skyline document " + skyZipPath + " into Panorama folder '" + containerPath + "'");

        ClientActionUpload cmd = new ClientActionUpload();
        WebdavUrlParts uploadToWebdavUrlParts = webdavUrlParts != null ? webdavUrlParts : new WebdavUrlParts(serverUri, containerPath, "");
        cmd.uploadFile(uploadToWebdavUrlParts, skyZipPath, connection);
        LOG.info("Uploaded Skyline document " + skyZipPath + " to " + uploadToWebdavUrlParts.combinePartsQuoted());

        importSkylineDocument(labKeyUrlParts, new File(skyZipPath).getName(), getFolderRelativeSkyZipPath(uploadToWebdavUrlParts), connection);
    }

    private String getFolderRelativeSkyZipPath(WebdavUrlParts webdavUrlParts)
    {
        return "".equals(webdavUrlParts.getPathInFwp()) ? "./" : webdavUrlParts.getPathInFwp();
    }

    private void importDocumentOnServer(LabKeyUrlParts labKeyUrlParts, String skyDocNameRemote, WebdavUrlParts webdavUrlParts, Connection connection) throws ClientException
    {
        // URLEncodedUtils.parsePathSegments will decode percent encoded octets, but will not replace '+' with space character.
        List<String> pathSegments = URLEncodedUtils.parsePathSegments(skyDocNameRemote);
        skyDocNameRemote = pathSegments.size() > 0 ? pathSegments.get(0) : skyDocNameRemote;

        if (!skyDocNameRemote.toLowerCase().endsWith(".sky.zip"))
        {
            throw new ClientException("Not a Skyline shared zip file " + skyDocNameRemote);
        }

        if (webdavUrlParts == null)
        {
            webdavUrlParts = new WebdavUrlParts(labKeyUrlParts.getServerUrl(), labKeyUrlParts.getContainerPath(), "");
        }
        WebdavUrlParts skyZipWebDavUrl = webdavUrlParts.appendToWebdavPath(skyDocNameRemote);
        if (!documentExistsInPanoramaFolder(skyZipWebDavUrl, connection))
        {
            throw new ClientException("Skyline document does not exist: " + skyZipWebDavUrl.combinePartsQuoted());
        }

        LOG.info("Starting import of Skyline document " + skyDocNameRemote + " into Panorama folder '" + labKeyUrlParts.getContainerPath() + "'");
        importSkylineDocument(labKeyUrlParts, skyDocNameRemote, getFolderRelativeSkyZipPath(webdavUrlParts), connection);
    }

//    private String getDecodedFileName(String skyDocNameRemote, LabKeyUrlParts urlParts) throws ClientException
//    {
//        // Plus sign "+" is converted into a space character "   " by URLDecoder. So an un-encoded file name like
//        // CCS library+small.sky.zip will get 'decoded' to 'CCS library small.sky.zip'. URLDecoder (Encoder) should
//        // be used on the query component, not the path component. Reserved characters are different in the path vs query.
//        // return URLDecoder.decode(skyDocNameRemote, StandardCharsets.UTF_8);

//        // Hacky way to make sure that we get a decoded name of the sky.zip file
//        LabKeyUrlParts tempParts = URLHelper.buildLabKeyUrlParts(urlParts.getServerUrl() + "/" + skyDocNameRemote + "/controller-action.view");
//        return tempParts.getContainerPath();
//    }

    private boolean documentExistsInPanoramaFolder(WebdavUrlParts webdavUrlParts, Connection connection) throws ClientException
    {
        WebDavCommand.CheckWebdavPathExists cmd = new WebDavCommand.CheckWebdavPathExists();
        try
        {
            CommandResponse response = cmd.check(connection, webdavUrlParts.getContainerPath(), webdavUrlParts.getPathInFwp());
            if (response.getStatusCode() != 200)
            {
                return false;
            }
        }
        catch (IOException | CommandException e)
        {
            if(e instanceof CommandException && ((CommandException)e).getStatusCode() == 404)
            {
                return false;
            }
            throw new ClientException("Error checking if Skyline document exists: "
                    + webdavUrlParts.combinePartsQuoted()
                    + ". Error was: " + e.getMessage(), e);
        }
        return true;
    }

    private void importSkylineDocument(LabKeyUrlParts labKeyUrlParts, String skyZipName, String skyZipServerPath, Connection connection) throws ClientException
    {
        PostCommand<CommandResponse> importCmd = new PostCommand<>("targetedms", "skylineDocUploadApi");
        Map<String, Object> params = new HashMap<>();
        params.put("path", skyZipServerPath);
        params.put("file", skyZipName);
        importCmd.setParameters(params);

        Long jobId;
        String serverUri = labKeyUrlParts.getServerUrl();
        String containerPath = labKeyUrlParts.getContainerPath();
        try
        {
            LOG.info("Starting Skyline document import");
            CommandResponse response = importCmd.execute(connection, containerPath);
            if (response.getStatusCode() != 200)
            {
                throw new ClientException("Error starting Skyline document import. Received HTTP status code " + response.getStatusCode());
            }

            jobId = response.getProperty("UploadedJobDetails[0].RowId");
        }
        catch (IOException | CommandException e)
        {
            throw new ClientException("Error starting Skyline document import on the server.", e);
        }

        // Example: https://panoramaweb-dr.gs.washington.edu/pipeline-status/00Developer/vsharma/PanoramaClientTest/details.view?rowId=85274
        // Example: https://panoramaweb-dr.gs.washington.edu/00Developer/vsharma/PanoramaClientTest/pipeline-status-details.view?rowId=85274
        String pipelineStatusUri = serverUri + "/" + containerPath + "/pipeline-status-details.view?rowId=" + jobId;
        LOG.info("Job submitted at: " + pipelineStatusUri + ". Checking status");
        try
        {
            SelectRowsCommand selectJobStatus = new SelectRowsCommand("pipeline", "job");
            selectJobStatus.setColumns(Collections.singletonList("Status"));
            Filter filter = new Filter("rowId", jobId);
            selectJobStatus.addFilter(filter);

            int i = 0;
            String status = null;
            while(!jobDone(status))
            {
                Thread.sleep(6 * 1000);

                status = getStatus(connection, containerPath, selectJobStatus, jobId);

                if(i % 10 == 0)
                {
                    LOG.info("Job status is: " + status);
                }
                i++;
            }
            LOG.info("Job done: " + pipelineStatusUri);
            LOG.info("Job status: " + status);
            if(!isComplete(status))
            {
                throw new ClientException("Skyline document was not imported. Error details can be found at " + pipelineStatusUri);
            }
        }
        catch (IOException | CommandException | InterruptedException e)
        {
            throw new ClientException("Error checking status of jobId " + jobId, e);
        }
    }

    private String getStatus(Connection connection, String containerPath, SelectRowsCommand cmd, long jobId) throws IOException, CommandException, ClientException, InterruptedException
    {
        int tryCount = 0;
        int maxTryCount = 5;
        while(++tryCount <= maxTryCount)
        {
            SelectRowsResponse response = cmd.execute(connection, containerPath);
            if(response.getStatusCode() == 200)
            {
                if(response.getRowCount().equals(0))
                {
                    throw new ClientException("No status returned for jobId " + jobId);
                }
                return (String) response.getRows().get(0).get("Status");
            }
            else
            {
                LOG.warn("Checking job status. Received unexpected HTTP status code " + response.getStatusCode());
            }
            Thread.sleep(5 * 1000); // Try again after 5 seconds.
        }
        throw new ClientException("Could not get status of jobId " + jobId + ". Giving up after trying " + maxTryCount + " times.");
    }

    private boolean jobDone(String status)
    {
        return status != null && (!(status.toLowerCase().contains("running") || status.toLowerCase().contains("waiting")));
    }

    private boolean isComplete(String status)
    {
        return status != null && status.toLowerCase().contains("complete");
    }
}
