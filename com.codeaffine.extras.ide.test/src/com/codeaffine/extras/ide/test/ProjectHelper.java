package com.codeaffine.extras.ide.test;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.core.resources.IContainer.INCLUDE_HIDDEN;
import static org.eclipse.core.resources.IResource.ALWAYS_DELETE_PROJECT_CONTENT;
import static org.eclipse.core.resources.IResource.FORCE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.ui.ide.undo.CreateProjectOperation;
import org.junit.rules.ExternalResource;

import com.google.common.base.Charsets;

public class ProjectHelper extends ExternalResource {

  private static int uniqueId = 0;
  private static List<ProjectHelper> projects = new LinkedList<ProjectHelper>();

  public static void cleanWorkspace() throws CoreException {
    projects.clear();
    IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects( INCLUDE_HIDDEN );
    for( IProject project : allProjects ) {
      delete( project );
      project.refreshLocal( IResource.DEPTH_ZERO, new NullProgressMonitor() );
    }
  }

  private final String projectName;
  private final IPath projectLocation;
  private IProject project;

  public ProjectHelper() {
    this.projectName = uniqueProjectName();
    this.projectLocation = null;
  }

  public String getName() {
    initializeProject();
    return project.getName();
  }

  public IProject getProject() {
    initializeProject();
    return project;
  }

  public IFolder createFolder( String name ) throws CoreException {
    initializeProject();
    String[] segments = new Path( name ).segments();
    IContainer container = project;
    for( String segment : segments ) {
      IFolder newFolder = container.getFolder( new Path( segment ) );
      if( !newFolder.exists() ) {
        newFolder.create( true, true, newProgressMonitor() );
      }
      container = newFolder;
    }
    return project.getFolder( new Path( name ) );
  }

  public IFile createFile( String fileName, String content ) throws CoreException {
    return createFile( getProject(), fileName, content );
  }

  public IFile createFile( IContainer parent, String fileName, String content )
    throws CoreException
  {
    initializeProject();
    IFile result = parent.getFile( new Path( fileName ) );
    InputStream stream = new ByteArrayInputStream( content.getBytes( Charsets.UTF_8 ) );
    if( !result.exists() ) {
      result.create( stream, true, newProgressMonitor() );
    } else {
      result.setContents( stream, false, false, newProgressMonitor() );
    }
    return result;
  }

  @Override
  protected void after() {
    if( isProjectCreated() ) {
      projects.remove( this );
      delete( project );
    }
  }

  private boolean isProjectCreated() {
    return project != null;
  }

  private void initializeProject() {
    if( !isProjectCreated() ) {
      IProjectDescription projectDescription = createProjectDescription();
      project = createProject( projectDescription );
      projects.add( this );
    }
  }

  private IProject createProject( IProjectDescription projectDescription ) {
    String label = "Create project " + projectName;
    try {
      new CreateProjectOperation( projectDescription, label ).execute( newProgressMonitor(), null );
    } catch( ExecutionException ee ) {
      throw new RuntimeException( ee );
    }
    return ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );
  }

  private IProjectDescription createProjectDescription() {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    IProjectDescription result = workspace.newProjectDescription( projectName );
    result.setLocation( projectLocation );
    return result;
  }

  private static IProgressMonitor newProgressMonitor() {
    return new NullProgressMonitor();
  }

  private static String uniqueProjectName() {
    String result = "test.project." + uniqueId;
    uniqueId++;
    return result;
  }

  public static void delete( IResource resource ) {
    int numAttempts = 0;
    boolean success = false;
    while( !success ) {
      try {
        resource.delete( FORCE | ALWAYS_DELETE_PROJECT_CONTENT, new NullProgressMonitor() );
        success = true;
        numAttempts++;
      } catch( CoreException ce ) {
        if( numAttempts > 4 ) {
          throw new RuntimeException( "Failed to delete resource: " + resource, ce );
        }
        System.gc();
        sleepUninterruptibly( 500, MILLISECONDS );
        System.gc();
      }
    }
  }

}
