package edu.maccosslab.panoramaclient;

import org.labkey.remoteapi.Connection;

import java.io.File;
import java.util.List;

public class ClientActionDownloadFiles extends ClientAction<ActionOptions.DownloadFiles>
{
    @Override
    public void doAction(ActionOptions.DownloadFiles options) throws ClientException
    {
        WebdavUrlParts webdavUrlParts = ClientAction.getWebdavUrl(options.getWebdavUrl());

        String downloadDir = options.getDestDirPath();
        if(downloadDir == null || downloadDir.trim().length() == 0)
        {
            downloadDir = System.getProperty("user.dir");
        }
        else
        {
            File dir = new File(downloadDir);
            if(!dir.exists())
            {
                throw new ClientException("Destination directory does not exist: " + downloadDir);
            }
            if(!dir.isDirectory())
            {
                throw new ClientException("Destination path is not a directory: " + downloadDir);
            }
        }

        downloadFiles(webdavUrlParts, downloadDir, options.getExtension(), options.getApiKey());
    }

    public void downloadFiles(WebdavUrlParts webdavUrlParts, String targetFolder, String extension, String apiKey) throws ClientException
    {
        Connection connection = getConnection(webdavUrlParts, apiKey);
        downloadFiles(webdavUrlParts.getContainerPath(), webdavUrlParts.getPathInFwp(), targetFolder, extension, connection);
    }

    public void downloadFiles(String containerPath, String fwpFolderPath, String targetFolder, String extension, Connection connection) throws ClientException
    {
        LOG.info("Files will be downloaded to " + targetFolder);

        ClientActionListFiles cmdListFiles = new ClientActionListFiles();
        List<String> fileNames = cmdListFiles.listFiles(containerPath, fwpFolderPath, extension, connection);
        if(fileNames != null)
        {
            if(fileNames.size() == 0)
            {
                LOG.info("No files " + (extension != null ? "matching the extension \"" + extension : "\"") + " found in containerPath " + containerPath + " and FWP folder " + fwpFolderPath );
            }

            String pathStringForMsg = " container " + containerPath + (fwpFolderPath.length() > 0 ? " and FWP folder " + fwpFolderPath : "");
            LOG.info("Found " + fileNames.size() + " files " + (extension != null ? "matching the extension \"" + extension + "\"" : "") + " in " + pathStringForMsg);
            ClientActionDownload cmdDownload = new ClientActionDownload();
            for(String sourceFile: fileNames)
            {
                String sourceFilePath = fwpFolderPath.length() > 0 ? fwpFolderPath + "/" + sourceFile : sourceFile;
                cmdDownload.downloadFile(containerPath, sourceFilePath, targetFolder, connection);
            }
        }
    }
}
