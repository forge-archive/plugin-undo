/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.undo.forge.touch;

import javax.inject.Inject;

import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.DefaultCommand;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 * 
 */
@Alias("touch")
public class TouchPlugin implements Plugin
{
   @Inject
   private Shell shell;

   @DefaultCommand
   public void touch(
            PipeOut out,
            @Option(name = "filename", shortName = "f", description = "File to be created") final String filename,
            @Option(name = "contents", shortName = "c", description = "File content to be inserted on file creation") final String contents
            ) throws Exception
   {
      if (filename == null)
         return;

      DirectoryResource dir = shell.getCurrentDirectory();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);

      if (file.exists())
      {
         out.println("File already exists: " + filename);
      }
      else
      {
         boolean isCreated = file.createNewFile();

         if (isCreated == false)
         {
            throw new RuntimeException("Error creating new file.");
         }

         out.println("File was successfully created: " + filename);
      }

      if (contents != null && contents.length() > 0)
      {
         file.setContents(contents);
         out.println("File contents updated");
      }
   }
}
