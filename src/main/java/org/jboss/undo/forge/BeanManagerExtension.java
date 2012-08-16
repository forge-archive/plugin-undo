package org.jboss.undo.forge;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import org.jboss.forge.shell.events.Shutdown;

public class BeanManagerExtension implements Extension
{
   private static volatile BeanManager beanManager;

   public void init(@Observes AfterDeploymentValidation evt, BeanManager bm)
   {
      BeanManagerExtension.beanManager = bm;
   }

   public void destroy(@Observes Shutdown forgeShutdown)
   {
      BeanManagerExtension.beanManager = null;
   }

   public static BeanManager getBeanManager()
   {
      return beanManager;
   }
}