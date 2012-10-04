package com.redhat.ceylon.eclipse.core.builder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;

import com.redhat.ceylon.common.config.CeylonConfig;
import com.redhat.ceylon.common.config.ConfigParser;
import com.redhat.ceylon.common.config.ConfigWriter;
import com.redhat.ceylon.common.config.Repositories;
import com.redhat.ceylon.common.config.Repositories.Repository;

public class CeylonProjectConfig {

    private static final Map<IProject, CeylonProjectConfig> PROJECT_CONFIGS = new HashMap<IProject, CeylonProjectConfig>();

    public static CeylonProjectConfig get(IProject project) {
        CeylonProjectConfig projectConfig = PROJECT_CONFIGS.get(project);
        if (projectConfig == null) {
            projectConfig = new CeylonProjectConfig(project);
            PROJECT_CONFIGS.put(project, projectConfig);
        }
        return projectConfig;
    }

    public static void remove(IProject project) {
        PROJECT_CONFIGS.remove(project);
    }

    private final IProject project;
    
    private Repositories mergedRepositories;
    private Repositories projectRepositories;
    private CeylonConfig projectConfig;
    
    private String transientOutputRepo;
    private List<String> transientProjectLookupRepos;

    private CeylonProjectConfig(IProject project) {
        this.project = project;
        initMergedRepositories();
        initProjectRepositories();
    }

    private void initMergedRepositories() {
        CeylonConfig mergedConfig = CeylonConfig.createFromLocalDir(project.getLocation().toFile());
        mergedRepositories = Repositories.withConfig(mergedConfig);
    }

    private void initProjectRepositories() {
        projectConfig = new CeylonConfig();
        File projectConfigFile = getProjectConfigFile();
        if (projectConfigFile.exists() && projectConfigFile.isFile()) {
            try {
                projectConfig = ConfigParser.loadConfigFromFile(projectConfigFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        projectRepositories = Repositories.withConfig(projectConfig);
    }

    public String getOutputRepo() {
        Repository outputRepo = mergedRepositories.getOutputRepository();
        return outputRepo.getUrl();
    }

    public void setOutputRepo(String outputRepo) {
        transientOutputRepo = outputRepo;
    }

    public IPath getOutputRepoPath() {
        Repository outputRepo = mergedRepositories.getOutputRepository();
        String outputRepoUrl = outputRepo.getUrl();

        IPath outputRepoPath;
        if (outputRepoUrl.startsWith("./")) {
            outputRepoPath = project.getFullPath().append(outputRepoUrl.substring(2));
        } else {
            outputRepoPath = project.getFullPath().append(outputRepoUrl);
        }

        return outputRepoPath;
    }

    public List<String> getGlobalLookupRepos() {
        List<String> result = new ArrayList<String>();
        Repository[] globalLookupRepos = mergedRepositories.getGlobalLookupRepositories();
        if (globalLookupRepos != null) {
            for (Repository globalLookupRepo : globalLookupRepos) {
                result.add(globalLookupRepo.getUrl());
            }
        }
        return result;
    }

    public List<String> getProjectLookupRepos() {
        List<String> result = new ArrayList<String>();
        Repository[] projectlookupRepos = projectRepositories.getRepositoriesByType(Repositories.REPO_TYPE_LOCAL_LOOKUP);
        if (projectlookupRepos != null) {
            for (Repository projectLookupRepo : projectlookupRepos) {
                result.add(projectLookupRepo.getUrl());
            }
        }
        return result;
    }
    
    public void setProjectLookupRepos(List<String> projectLookupRepos) {
        transientProjectLookupRepos = projectLookupRepos;
    }

    public void save() {
        initProjectRepositories();
        
        String oldOutputRepo = getOutputRepo();
        List<String> oldProjectLookupRepos = getProjectLookupRepos();
        
        boolean isOutputRepoChanged = transientOutputRepo != null && !transientOutputRepo.equals(oldOutputRepo);
        boolean isProjectLookupReposChanged = transientProjectLookupRepos != null && !transientProjectLookupRepos.equals(oldProjectLookupRepos);
        
        if (isOutputRepoChanged) {
            deleteOldOutputFolder(oldOutputRepo);
            createNewOutputFolder();
        }
        
        if (isOutputRepoChanged || isProjectLookupReposChanged) {
            try {
                if (isOutputRepoChanged) {
                    Repository newOutputRepo = new Repositories.SimpleRepository("", transientOutputRepo, null);
                    projectRepositories.setRepositoriesByType(Repositories.REPO_TYPE_OUTPUT, new Repository[] { newOutputRepo });
                }

                if (isProjectLookupReposChanged) {
                    Repository[] newProjectLookupRepos = new Repository[transientProjectLookupRepos.size()];
                    for (int i = 0; i < transientProjectLookupRepos.size(); i++) {
                        newProjectLookupRepos[i] = new Repositories.SimpleRepository("", transientProjectLookupRepos.get(i), null);
                    }
                    projectRepositories.setRepositoriesByType(Repositories.REPO_TYPE_LOCAL_LOOKUP, newProjectLookupRepos);
                }

                ConfigWriter.write(projectConfig, getProjectConfigFile());
                initMergedRepositories();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void deleteOldOutputFolder(String oldOutputRepo) {
        IFolder oldOutputRepoFolder = project.getFolder(removeCurrentDirPrefix(oldOutputRepo));
        if( oldOutputRepoFolder.exists() ) {
            boolean remove = MessageDialog.openQuestion(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), 
                    "Changing Ceylon output repository", 
                    "The Ceylon output repository has changed. Do you want to remove the old output repository folder '" + oldOutputRepoFolder.getFullPath().toString() + "' and all its contents?");
            if (remove) {
                try {
                    oldOutputRepoFolder.delete(true, null);
                } catch (CoreException e) {
                    e.printStackTrace();
                }
            }
        }
        if (oldOutputRepoFolder.exists() && oldOutputRepoFolder.isHidden()) {
            try {
                oldOutputRepoFolder.setHidden(false);
            } catch (CoreException e) {
                e.printStackTrace();
            }
        }
    }

    private void createNewOutputFolder() {
        IFolder newOutputRepoFolder = project.getFolder(removeCurrentDirPrefix(transientOutputRepo));
        if (!newOutputRepoFolder.exists()) {
            try {
                CoreUtility.createDerivedFolder(newOutputRepoFolder, true, true, null);
            } catch (CoreException e) {
                e.printStackTrace();
            }
        }
        if (!newOutputRepoFolder.isHidden()) {
            try {
                newOutputRepoFolder.setHidden(true);
            } catch (CoreException e) {
                e.printStackTrace();
            }
        }
    }

    private File getProjectConfigFile() {
        return new File(project.getLocation().toFile(), ".ceylon/config");
    }

    private String removeCurrentDirPrefix(String url) {
        return url.startsWith("./") ? url.substring(2) : url;
    }

}