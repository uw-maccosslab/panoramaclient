package edu.maccosslab.panoramaclient;

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
import java.util.Map;

public class ClientActionImportSkyDoc extends ClientAction<ActionOptions.ImportSkyDoc>
{
    @Override
    public void doAction(ActionOptions.ImportSkyDoc options) throws ClientException
    {
        String skyDocPath = options.getSkyDocPath();

        File file = new File(skyDocPath);
        if(!file.exists())
        {
            throw new ClientException("Source file does not exist: " + skyDocPath);
        }
        if(!file.isFile())
        {
            throw new ClientException("Not a file " + skyDocPath);
        }
        if(!skyDocPath.toLowerCase().endsWith(".sky.zip"))
        {
            throw new ClientException("Not a Skyline shared zip file " + file.getName());
        }

        String panoramaFolderUrl = options.getPanoramaFolderUrl();
        if(panoramaFolderUrl == null || panoramaFolderUrl.trim().length() == 0)
        {
            throw new ClientException("URL string cannot be empty");
        }

        LabKeyUrlParts labKeyUrlParts = URLHelper.buildLabKeyUrlParts(panoramaFolderUrl);

        Connection connection = getConnection(labKeyUrlParts.getServerUrl(), options.getApiKey());

        uploadAndImport(labKeyUrlParts.getServerUrl(), labKeyUrlParts.getContainerPath(), options.getSkyDocPath(), connection);
    }

    void uploadAndImport(String serverUri, String containerPath, String skyZipPath, Connection connection) throws ClientException
    {
        LOG.info("Starting upload and import of Skyline document " + skyZipPath + " into Panorama folder " + containerPath);

        ClientActionUpload cmd = new ClientActionUpload();
        cmd.uploadFile(containerPath, "", skyZipPath, connection);
        LOG.info("Uploaded Skyline document " + skyZipPath + " into Panorama folder " + containerPath);

        PostCommand importCmd = new PostCommand("targetedms", "skylineDocUploadApi");
        Map<String, Object> params = new HashMap<>();
        params.put("path", "./");
        params.put("file", new File(skyZipPath).getName());
        importCmd.setParameters(params);

        try
        {
            LOG.info("Starting Skyline document import");
            CommandResponse response = importCmd.execute(connection, containerPath);
            if (response.getStatusCode() != 200)
            {
                throw new ClientException("Received HTTP status code " + response.getStatusCode());
            }

            Long jobId = response.getProperty("UploadedJobDetails[0].RowId");
            // Example: https://panoramaweb-dr.gs.washington.edu/pipeline-status/00Developer/vsharma/PanoramaClientTest/details.view?rowId=85274
            // Example: https://panoramaweb-dr.gs.washington.edu/00Developer/vsharma/PanoramaClientTest/pipeline-status-details.view?rowId=85274
            String pipelineStatusUri = serverUri + "/" + containerPath + "/pipeline-status-details.view?rowId=" + jobId;
            LOG.info("Job submitted at: " + pipelineStatusUri +". Checking status");

            SelectRowsCommand selectJobStatus = new SelectRowsCommand("pipeline", "job");
            selectJobStatus.setColumns(Collections.singletonList("Status"));
            Filter filter = new Filter("rowId", jobId);
            selectJobStatus.addFilter(filter);

            int i = 0;
            String status = null;
            while(!jobDone(status))
            {
                Thread.sleep(5 * 1000);

                status = getStatus(connection, containerPath, selectJobStatus, jobId);

                if(i % 10 == 0)
                {
                    LOG.info("Job status is: " + status);
                }
                i++;
            }
            LOG.info("Job done: " + pipelineStatusUri);
        }
        catch (IOException | CommandException | InterruptedException e)
        {
            throw new ClientException("Error importing Skyline document on server", e);
        }
    }

    private String getStatus(Connection connection, String containerPath, SelectRowsCommand cmd, long jobId) throws IOException, CommandException, ClientException
    {
        SelectRowsResponse response = cmd.execute(connection, containerPath);
        if (response.getStatusCode() != 200)
        {
            //TODO: Check 5 times before giving up
            throw new ClientException("Received HTTP status code " + response.getStatusCode());
        }
        if(response.getRowCount().equals(0))
        {
            throw new ClientException("No status returned for jobId " + jobId);
        }
        String status = (String) response.getRows().get(0).get("Status");
        return status;
    }

    private boolean jobDone(String status)
    {
        return status != null && (!(status.toLowerCase().contains("running") || status.toLowerCase().contains("waiting")));
    }
}
