package edu.maccosslab.panoramaclient;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.json.simple.JSONObject;
import org.labkey.remoteapi.Command;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.CommandResponse;
import org.labkey.remoteapi.Connection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class WebDavCommand<ResponseType extends CommandResponse> extends Command<ResponseType>
{
    public WebDavCommand()
    {
        super(URLHelper.WEBDAV, "NO_ACTION");
    }

    @Override
    protected String getQueryString()
    {
        return null;
    }

    @Override
    protected URI getActionUrl(Connection connection, String folderPath) throws URISyntaxException
    {
        URI uri = new URI(connection.getBaseUrl().replace('\\', '/'));
        StringBuilder path = new StringBuilder(uri.getPath() != null && !"".equals(uri.getPath()) ? uri.getPath() : "/");
        String controller = this.getControllerName();
        if (controller.charAt(0) != '/' && path.charAt(path.length() - 1) != '/') {
            path.append('/');
        }

        path.append(controller);
        String folder;
        if (null != folderPath && folderPath.length() > 0) {
            folder = folderPath.replace('\\', '/');
            if (folder.charAt(0) != '/' && path.charAt(path.length() - 1) != '/') {
                path.append('/');
            }

            path.append(folder);
        }

        if(path.charAt(path.length() - 1) != '/')
        {
            path.append('/');
        }
        path.append(URLHelper.FILE_ROOT);
        String pathAfterFileRoot = pathAfterFileRoot();
        if(pathAfterFileRoot != null && pathAfterFileRoot.length() > 0 && pathAfterFileRoot.charAt(0) != '/')
        {
            path.append('/');
        }
        path.append(pathAfterFileRoot);

        URI toReturn = (new URIBuilder(uri)).setPath(path.toString()).build();
        return toReturn;
    }

    abstract String pathAfterFileRoot();

    public static class Upload extends WebDavCommand
    {
        private String _sourceFilePath;
        private String _folderPath; // Part of the path after the file root. e.g sub-folder path in the FWP.

        public CommandResponse upload(Connection connection, String containerPath, String sourceFilePath) throws IOException, CommandException
        {
            return upload(connection, containerPath, null, sourceFilePath);
        }

        public CommandResponse upload(Connection connection, String containerPath, String folderPath, String sourceFilePath) throws IOException, CommandException
        {
            this._sourceFilePath = sourceFilePath;
            _folderPath = folderPath;
            return super.execute(connection, containerPath);
        }

        @Override
        String pathAfterFileRoot()
        {
            return (_folderPath != null && _folderPath.trim().length() > 0) ? _folderPath.trim() : "";
        }

        @Override
        protected HttpUriRequest createRequest(URI uri)
        {
            HttpPost request = new HttpPost(uri);
            HttpEntity multipartEntity = MultipartEntityBuilder.create().addBinaryBody("file", new File(_sourceFilePath)).build();
            request.setEntity(multipartEntity);
            return request;
        }
    }

    public static class Download extends WebDavCommand
    {
        private String _sourceFile;

        @Override
        String pathAfterFileRoot()
        {
            return '/' + _sourceFile;
        }

        public CommandResponse download(Connection connection, String containerPath, String sourceFile, String targetFilePath) throws CommandException, IOException
        {
            _sourceFile = sourceFile;

            Response response = super._execute(connection, containerPath);
            try (BufferedInputStream is = new BufferedInputStream(response.getInputStream()))
            {
                try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(new File(targetFilePath))))
                {
                    byte[] bytes = new byte[8096];
                    int bytesRead;
                    while ((bytesRead = is.read(bytes)) != -1)
                    {
                        fos.write(bytes, 0, bytesRead);
                    }
                }
            }
            return createResponse(response.getText(), response.getStatusCode(), response.getContentType(), new JSONObject());
        }
    }

    public static class ListFiles extends WebDavCommand<ListFilesResponse>
    {
        private String _folderPath; // Part of the path after the file root. e.g sub-folder path in the FWP.

        public ListFilesResponse list(Connection connection, String containerPath) throws IOException, CommandException
        {
            return list(connection, containerPath, null);
        }

        public ListFilesResponse list(Connection connection, String containerPath, String folderPath) throws IOException, CommandException
        {
            _folderPath = folderPath;
            return execute(connection, containerPath);
        }

        @Override
        String pathAfterFileRoot()
        {
            return (_folderPath != null && _folderPath.trim().length() > 0) ? _folderPath.trim() : "";
        }

        @Override
        protected HttpUriRequest createRequest(URI uri)
        {
            HttpEntityEnclosingRequestBase request = new HttpEntityEnclosingRequestBase()
            {
                @Override
                public String getMethod()
                {
                    // JSON for DavController.JsonAction
                    // PROPFIND for DavController.PropfindAction
                    return "JSON";
                }
            };
            request.setURI(uri);
            return request;
        }

        @Override
        protected ListFilesResponse createResponse(String text, int status, String contentType, JSONObject json)
        {
            return new ListFilesResponse(text, status, contentType, json, this);
        }
    }

    public static class ListFilesResponse extends CommandResponse
    {
        private List<String> _fileNames;

        public ListFilesResponse(String text, int statusCode, String contentType, JSONObject json, Command sourceCommand)
        {
            super(text, statusCode, contentType, json, sourceCommand);
        }

        public List<String> getFiles()
        {
            if (_fileNames == null)
            {
                List<Map<String, Object>> fileList = getProperty("files");
                if (fileList == null)
                    throw new IllegalStateException("No file list returned from the server.");

                _fileNames = new ArrayList<>();
                for (Map<String, Object> fileDetails: fileList)
                {
                    Object isCollection = fileDetails.get("collection");
                    if(isCollection != null && Boolean.valueOf(isCollection.toString()))
                    {
                        // Ignore directories
                        continue;
                    }
                    _fileNames.add((String) fileDetails.get("text")); // Just the filename
                }
            }
            return _fileNames;
        }
    }
}
