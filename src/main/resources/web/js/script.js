// app.js
const startTime = new Date().getTime();

import { initApp } from './init.js';
import { utilsApp } from './core.js';
import { listApp } from './list.js';
import { editApp } from './edit.js';
import { releaseApp } from './release.js';
import { chartApp } from './chart.js';
import { managementApp } from './management.js';
import { comboboxApp } from './components/title-selector.js';

/**
 * Creates the main application with combined functionality
 * from list and edit modules
 */
export function app() {
    let combinedApp = {};
    // Combine the list and edit functions using Object.assign
    try {
        combinedApp = Object.defineProperties({},
            {
                ...Object.getOwnPropertyDescriptors(utilsApp()),
                ...Object.getOwnPropertyDescriptors(listApp()),
                ...Object.getOwnPropertyDescriptors(editApp()),
                ...Object.getOwnPropertyDescriptors(releaseApp()),
                ...Object.getOwnPropertyDescriptors(chartApp()),
                ...Object.getOwnPropertyDescriptors(managementApp()),
                ...Object.getOwnPropertyDescriptors(initApp()),
                ...Object.getOwnPropertyDescriptors(comboboxApp()),
            }
        );
    } catch(e) {
        debugger;
        console.error("error: ", e);
    }

    return combinedApp;
}

document.addEventListener("DOMContentLoaded", function(event) {
    const currentTime = new Date().getTime();
    const elapsedTime = currentTime - startTime;
    const remainingTime = Math.max(10, 500 - elapsedTime);

    Alpine.data('app', app);
    Alpine.start();

    showLoader(remainingTime);
});

function showLoader(remainingTime) {
  const splashScreen = document.getElementsByClassName('splash-screen')[0];
  const mainContent = document.getElementsByClassName('main-content')[0];
  splashScreen.style.display = 'flex';
  splashScreen.style.opacity = '1';
  mainContent.style.opacity = '0';

  setTimeout(() => {
      splashScreen.style.opacity = '0';
      mainContent.style.opacity = '1';

      setTimeout(() => {
          splashScreen.style.display = 'none';
      }, 250);
  }, remainingTime);
}