package com.example.myapplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityScenarioRule =
            new ActivityScenarioRule<>(MainActivity.class);

//    @Test
//    public void testUIComponentsInitialized() {
//        onView(withId(R.id.editTextAccountName)).check(matches(isDisplayed()));
//        onView(withId(R.id.editTextUsername)).check(matches(isDisplayed()));
//        onView(withId(R.id.editTextPassword)).check(matches(isDisplayed()));
//        onView(withId(R.id.listViewPasswords)).check(matches(isDisplayed()));
//        onView(withId(R.id.imageViewCloud)).check(matches(isDisplayed()));
//        onView(withId(R.id.buttonAdd)).check(matches(isDisplayed()));
//    }

}
