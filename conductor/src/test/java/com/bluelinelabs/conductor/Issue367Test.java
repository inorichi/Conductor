package com.bluelinelabs.conductor;

import android.app.Activity;
import android.os.Bundle;

import com.bluelinelabs.conductor.internal.LifecycleHandler;
import com.bluelinelabs.conductor.util.ActivityProxy;
import com.bluelinelabs.conductor.util.TestController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class Issue367Test {

    @Test
    public void testViewIsAttachedAfterStartedActivityIsRecreated() {
        // Prepare a new activity
        ActivityProxy activityProxy = new ActivityProxy().create(null);

        // Attach router and set initial controller
        Controller controller = new TestController();
        Router router = Conductor.attachRouter(activityProxy.getActivity(), activityProxy.getView(), null);
        router.setRoot(RouterTransaction.with(controller).tag("root"));

        // Retrieve the lifecycle handler because Robolectric recreates another one instead of
        // reusing the instance when there's a configuration change
        LifecycleHandler lifecycleHandler = (LifecycleHandler) activityProxy.getActivity()
                .getFragmentManager().findFragmentByTag("LifecycleHandler");
        assertNotNull(lifecycleHandler);

        // Activity initialized and visible to the user
        activityProxy.start().resume();
        assertTrue(controller.isAttached());

        // Lock screen
        Bundle bundle = new Bundle();
        activityProxy.pause().saveInstanceState(bundle).stop(false);

        // Unlock screen
        activityProxy.start();
        assertTrue(controller.isAttached());

        // Activity was started but it changes configuration and it's being recreated
        activityProxy.getActivity().isChangingConfigurations = true;
        activityProxy.stop(true).destroy();

        // Activity recreated
        activityProxy = new ActivityProxy().create(bundle);

        // Simulate [Conductor::attachRouter] providing our LifecycleHandler instance
        registerActivityListener(lifecycleHandler, activityProxy.getActivity());
        router = lifecycleHandler.getRouter(activityProxy.getView(), bundle);
        router.rebindIfNeeded();

        // Retrieve our test controller, although this call isn't needed (same controller instance)
        controller = router.getControllerWithTag("root");

        // Activity resumed
        activityProxy.start().resume();

        // View is never attached
        assertTrue(controller.isAttached());
    }

    // Call [LifecycleHandler::registerActivityListener] with reflection to not make the method
    // public.
    private void registerActivityListener(LifecycleHandler handler, Activity activity) {
        try {
            Method method = LifecycleHandler.class.getDeclaredMethod("registerActivityListener",
                    Activity.class);
            method.setAccessible(true);
            method.invoke(handler, activity);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

}
