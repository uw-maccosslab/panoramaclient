package edu.maccosslab.panoramaclient;

import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;

import java.io.File;
import java.io.IOException;

public class ClientActionUpload extends ClientAction<ActionOptions.Upload>
{
    @Override
    public void doAction(ActionOptions.Upload options) throws ClientException
    {
        WebdavUrlParts webdavUrlParts = ClientAction.getWebdavUrl(options.getWebdavUrl());

        String srcFilePath = options.getSrcFilePath();

        File srcFile = new File(srcFilePath);
        if(!srcFile.exists())
        {
            throw new ClientException("Source file does not exist: " + srcFilePath);
        }
        if(!srcFile.isFile())
        {
            throw new ClientException("Not a file " + srcFilePath);
        }

        uploadFile(webdavUrlParts, srcFilePath, options.getApiKey());
    }

    void uploadFile(WebdavUrlParts webdavUrlParts, String srcFilePath, String apiKey) throws ClientException
    {
        Connection connection = getConnection(webdavUrlParts, apiKey);
        uploadFile(webdavUrlParts.getContainerPath(), webdavUrlParts.getPathInFwp(), srcFilePath, connection);
    }

    void uploadFile(String containerPath, String fwpFolderPath, String srcFilePath, Connection connection) throws ClientException
    {
        String pathStringForMsg = " container \'" + containerPath + "\'" + (fwpFolderPath.length() > 0 ? " and FWP folder \'" + fwpFolderPath + "\'"
                : "");
        LOG.info("Uploading " + srcFilePath + " to " + pathStringForMsg);

        WebDavCommand.Upload cmd = new WebDavCommand.Upload();
        try
        {
            CommandResponse response = cmd.upload(connection, containerPath, fwpFolderPath, srcFilePath);
            if (response.getStatusCode() != 200 && response.getStatusCode() != 207)
            {
                throw new ClientException("Received HTTP status code " + response.getStatusCode());
            }
        }
        catch (IOException | CommandException e)
        {
            throw new ClientException("Error uploading file to " + pathStringForMsg + ": " + e.getMessage(), e);
        }
    }
}
