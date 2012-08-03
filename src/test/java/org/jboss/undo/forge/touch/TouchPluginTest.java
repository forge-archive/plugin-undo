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

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.test.AbstractShellTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 *
 */
public class TouchPluginTest extends AbstractShellTest
{
   @Deployment
   public static JavaArchive getDeployment()
   {
      return AbstractShellTest.getDeployment().addPackages(true, TouchPlugin.class.getPackage());
   }

   @Test
   public void shouldCreateNewFile() throws Exception
   {
      String filename = "test.txt";

      queueInputLines("y");
      Project p = initializeJavaProject();

      getShell().execute("touch --filename " + filename);
      resetInputQueue();
      DirectoryResource dir = p.getProjectRoot();

      Assert.assertTrue("file doesn't exist", dir.getChild(filename).exists());
   }

   @Test
   public void shouldCreateaNewFileWithContents() throws Exception
   {
      String filename = "test2.txt";
      String contents = "foo bar baz";

      queueInputLines("y");
      Project p = initializeJavaProject();

      getShell().execute("touch --filename " + filename + " --contents " + Strings.enquote(contents));
      resetInputQueue();
      DirectoryResource dir = p.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertTrue("file doesn't exist", file.exists());

      BufferedReader reader = new BufferedReader(new InputStreamReader(file.getResourceInputStream()));
      Assert.assertEquals("file contents don't match", contents, reader.readLine().trim());
   }

   @Test
   public void shouldUpdateFileContents() throws Exception
   {
      String filename = "test3.txt";
      String contents1 = "foo bar baz";
      String contents2 = "baz";

      DirectoryResource dir = null;
      FileResource<?> file = null;
      BufferedReader reader = null;

      queueInputLines("y");
      Project p = initializeJavaProject();

      getShell().execute("touch --filename " + filename + " --contents " + Strings.enquote(contents1));
      resetInputQueue();
      dir = p.getProjectRoot();
      file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertTrue("file doesn't exist", file.exists());

      reader = new BufferedReader(new InputStreamReader(file.getResourceInputStream()));
      Assert.assertEquals("file contents don't match", contents1, reader.readLine().trim());

      getShell().execute("touch --filename " + filename + " --contents " + Strings.enquote(contents2));
      resetInputQueue();
      dir = p.getProjectRoot();
      file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertTrue("file doesn't exist", file.exists());

      reader = new BufferedReader(new InputStreamReader(file.getResourceInputStream()));
      Assert.assertEquals("file contents don't match", contents2, reader.readLine().trim());
   }

 }
