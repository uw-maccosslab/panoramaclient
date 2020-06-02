package edu.maccosslab.panoramaclient;

import java.net.URL;

public class WebdavUrlParts extends LabKeyUrlParts
{
    private final String _pathInFwp;

    public WebdavUrlParts(String serverUrl, String containerPath, String pathInFwp)
    {
        super(serverUrl, containerPath);
        _pathInFwp = pathInFwp;
    }

    public String getPathInFwp()
    {
        return _pathInFwp;
    }
}
