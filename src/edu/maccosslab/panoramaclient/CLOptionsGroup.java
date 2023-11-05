package edu.maccosslab.panoramaclient;

import org.apache.commons.cli.*;

import java.io.PrintWriter;

public abstract class CLOptionsGroup<T extends ActionOptions>
{
    private final Option _mainOption;
    private final String _helpMessage;

    public static Option apiKeyOption = Option.builder("k").longOpt("api_key").hasArg(true).required(false).desc("Panorama server API key").build();
    public static Option webdavFolderOption = Option.builder("w").longOpt("webdav_url").hasArg(true).required(true).desc("WebDav URL of the folder on the Panorama server").build();
    public static Option destDownloadPathOption = Option.builder("t").longOpt("dest_download_path").hasArg(true).required(false).desc("Destination download folder path").build();
    public static Option fileExtOption = Option.builder("e").longOpt("extension").hasArg(true).required(false).desc("File extension").build();

    public CLOptionsGroup(Option mainOption, String helpMessage)
    {
        _mainOption = mainOption;
        _helpMessage = helpMessage;
    }

    public Option getMainOption()
    {
        return _mainOption;
    }

    public String commandLineSyntax()
    {
        return "java -jar panoramaclient.jar -" + _mainOption.getOpt();
    }

    public void printHelp(PrintWriter pw, HelpFormatter hf)
    {
        pw.println(_helpMessage);
        hf.printHelp(pw, hf.getWidth(),
                commandLineSyntax(),
                null,
                getSubOptions(),
                hf.getLeftPadding(),
                hf.getDescPadding(),
                null,
                true);
    }

    public void printHelp()
    {
        HelpFormatter hf = new HelpFormatter();
        hf.setOptionComparator((o1, o2) -> {
            int cmp = Boolean.compare(o2.isRequired(), o1.isRequired());
            if(cmp != 0) return cmp;
            cmp = Boolean.compare(o2.hasArg(), o1.hasArg());
            if(cmp != 0) return cmp;

            else
            {
                return o1.getOpt().compareTo(o2.getOpt());
            }
        });
        try (PrintWriter pw = new PrintWriter(System.out))
        {
            printHelp(pw, hf);
        }
    }

    CommandLine parseCommandLine(String[] args) throws ParseException
    {
        CommandLineParser parser = new DefaultParser();

        CommandLine cl;

        Options optsToParse = getOptionsToParse();

        cl = parser.parse(optsToParse, args);

        if (cl == null || cl.getOptions().length == 0)
        {
            throw new ParseException("No options parsed from the command-line.");
        }
        return cl;
    }

    private Options getOptionsToParse()
    {
        Options optsToParse = new Options();
        optsToParse.addOption(getMainOption());
        Options subOptions = getSubOptions();
        for(Option opt: subOptions.getOptions())
        {
            optsToParse.addOption(opt);
        }
        return optsToParse;
    }

    public abstract T getActionOptions(String[] args) throws ParseException;
    public abstract Options getSubOptions();

    public static class DownloadFile extends CLOptionsGroup<ActionOptions.Download>
    {
        private static final String description = "Download a file";
        public static Option downloadFileOpt = Option.builder("d").required(true).hasArg(false).longOpt("download").desc(description).build();
        public static Option webdavFileOption = Option.builder("w").longOpt("webdav_url").hasArg(true).required(true).desc("WebDav URL of the file on the Panorama server").build();

        public DownloadFile()
        {
            super(downloadFileOpt, description);
        }

        @Override
        public Options getSubOptions()
        {
            Options options = new Options();
            options.addOption(webdavFileOption);
            options.addOption(apiKeyOption);
            options.addOption(destDownloadPathOption);
            return options;
        }

        @Override
        public ActionOptions.Download getActionOptions(String[] args) throws ParseException
        {
            CommandLine cl = parseCommandLine(args);
            ActionOptions.Download opts = new ActionOptions.Download();
            opts.setWebdavUrl(cl.getOptionValue(webdavFileOption.getOpt()));
            opts.setDestDirPath(cl.getOptionValue(destDownloadPathOption.getOpt()));
            opts.setApiKey(cl.getOptionValue(apiKeyOption.getOpt()));
            return opts;
        }
    }

    public static class DownloadFiles extends CLOptionsGroup<ActionOptions.DownloadFiles>
    {
        private static final Option downloadFilesOpt = Option.builder("a").required(true).hasArg(false).longOpt("download_files").desc("Download files from a Panorama folder").build();

        public DownloadFiles()
        {
            super(downloadFilesOpt, "Download files from a Panorama folder");
        }

        @Override
        public Options getSubOptions()
        {
            Options options = new Options();
            options.addOption(webdavFolderOption);
            options.addOption(destDownloadPathOption);
            options.addOption(fileExtOption);
            options.addOption(apiKeyOption);
            return options;
        }

        @Override
        public ActionOptions.DownloadFiles getActionOptions(String[] args) throws ParseException
        {
            CommandLine cl = parseCommandLine(args);
            ActionOptions.DownloadFiles opts = new ActionOptions.DownloadFiles();
            opts.setWebdavUrl(cl.getOptionValue(webdavFolderOption.getOpt()));
            opts.setDestDirPath(cl.getOptionValue(destDownloadPathOption.getOpt()));
            opts.setApiKey(cl.getOptionValue(apiKeyOption.getOpt()));
            opts.setExtension(cl.getOptionValue(fileExtOption.getOpt()));
            return opts;
        }
    }

    public static class UploadFile extends CLOptionsGroup<ActionOptions.Upload>
    {
        private static final String description = "Upload a file";
        private static final Option uploadFileOpt = Option.builder("u").required().hasArg(false).longOpt("upload").desc(description).build();
        private static final Option srcFilePathOption = Option.builder("f").longOpt("source_file_path").hasArg(true).required(true).desc("Path of the file to be uploaded").build();
        private static final Option createDirIfNotExistsOption = Option.builder("c").longOpt("create_dir").hasArg(false).required(false).desc("Create the target directory if it does not exist").build();

        public UploadFile()
        {
            super(uploadFileOpt, description);
        }

        @Override
        public Options getSubOptions()
        {
            Options options = new Options();
            options.addOption(createDirIfNotExistsOption);
            options.addOption(srcFilePathOption);
            options.addOption(webdavFolderOption);
            options.addOption(apiKeyOption);
            return options;
        }

        @Override
        public ActionOptions.Upload getActionOptions(String[] args) throws ParseException
        {
            CommandLine cl = parseCommandLine(args);
            ActionOptions.Upload opts = new ActionOptions.Upload();
            opts.setCreateTargetDir(cl.hasOption(createDirIfNotExistsOption.getOpt()));
            opts.setSrcFilePath(cl.getOptionValue(srcFilePathOption.getOpt()));
            opts.setWebdavUrl(cl.getOptionValue(webdavFolderOption.getOpt()));
            opts.setApiKey(cl.getOptionValue(apiKeyOption.getOpt()));
            return opts;
        }
    }

    public static class ListFiles extends CLOptionsGroup<ActionOptions.ListFiles>
    {
        private static final String description = "List files in a Panorama folder";
        private static final Option listFilesOpt = Option.builder("l").required().hasArg(false).longOpt("list_files").desc(description).build();
        static final Option outputFileOpt = Option.builder("o").longOpt("output_file").hasArg(true).required(false).desc("Output file").build();

        public ListFiles()
        {
            super(listFilesOpt, description);
        }

        @Override
        public Options getSubOptions()
        {
            Options options = new Options();
            options.addOption(webdavFolderOption);
            options.addOption(fileExtOption);
            options.addOption(outputFileOpt);
            options.addOption(apiKeyOption);
            return options;
        }

        @Override
        public ActionOptions.ListFiles getActionOptions(String[] args) throws ParseException
        {
            CommandLine cl = parseCommandLine(args);
            ActionOptions.ListFiles opts = new ActionOptions.ListFiles();
            opts.setWebdavUrl(cl.getOptionValue(webdavFolderOption.getOpt()));
            opts.setExtension(cl.getOptionValue(fileExtOption.getOpt()));
            opts.setOutputFile(cl.getOptionValue(outputFileOpt.getOpt()));
            opts.setApiKey(cl.getOptionValue(apiKeyOption.getOpt()));
            return opts;
        }
    }

    public static class ImportSkyDoc extends CLOptionsGroup<ActionOptions.ImportSkyDoc>
    {
        private static final String description = "Import a Skyline document";
        public static Option importSkyDocOpt = Option.builder("i").required().hasArg(false).longOpt("import_skydoc").desc(description).build();
        public static Option panoramaFolderUrl = Option.builder("p").longOpt("panorama_folder_url").hasArg(true).required(true).desc("URL of the folder on the Panorama server").build();
        public static Option skydocPathLocalOpt = Option.builder("s").longOpt("skydoc_path").hasArg(true).required(false)
                .desc("Path of the Skyline document to be uploaded and imported. Only one of -s or -t should be specified.").build();
        public static Option skydocNameRemoteOpt = Option.builder("t").longOpt("skydoc_name_remote").hasArg(true).required(false)
                .desc("Name of the Skyline document to import. Document (sky.zip) must already exist in the Panorama folder. Only one of -s or -t should be specified.").build();
        public static Option webdavFolderUrlOption = Option.builder("w").longOpt("webdav_url").hasArg(true).required(false)
                .desc("WebDav URL of a directory in the Panorama folder." +
                        " If used with the -s option this is the directory where the Skyline document will be uploaded." +
                        " If used with the -t option this is the directory that contains the Skyline document." +
                        " This option is not required if the directory is the Panorama folder root (e.g. https://panoramaweb.org/_webdav/MyProject/MyFolder/@files)").build();

        public ImportSkyDoc()
        {
            super(importSkyDocOpt, description);
        }

        @Override
        public Options getSubOptions()
        {
            Options options = new Options();
            options.addOption(panoramaFolderUrl);
            options.addOption(skydocPathLocalOpt);
            options.addOption(skydocNameRemoteOpt);
            options.addOption(webdavFolderUrlOption);
            options.addOption(apiKeyOption);
            return options;
        }

        @Override
        public ActionOptions.ImportSkyDoc getActionOptions(String[] args) throws ParseException
        {
            CommandLine cl = parseCommandLine(args);
            ActionOptions.ImportSkyDoc opts = new ActionOptions.ImportSkyDoc();
            opts.setPanoramaFolderUrl(cl.getOptionValue(panoramaFolderUrl.getOpt()));
            opts.setSkyDocPathLocal(cl.getOptionValue(skydocPathLocalOpt.getOpt()));
            opts.setSkyDocNameRemote(cl.getOptionValue(skydocNameRemoteOpt.getOpt()));
            opts.setWebdavUrl(cl.getOptionValue(webdavFolderUrlOption.getOpt()));
            opts.setApiKey(cl.getOptionValue(apiKeyOption.getOpt()));
            if (opts.getSkyDocNameRemote() == null && opts.getSkyDocPathLocal() == null)
            {
                throw new ParseException("Please specify a value for one of the options: -" + skydocPathLocalOpt.getOpt() + " or -" + skydocNameRemoteOpt.getOpt() + ".");
            }
            if (opts.getSkyDocNameRemote() != null && opts.getSkyDocPathLocal() != null)
            {
                throw new ParseException("Please specify a value for ONLY one of the options: -" + skydocPathLocalOpt.getOpt() + " or -" + skydocNameRemoteOpt.getOpt() + ".");
            }
            return opts;
        }
    }
}

