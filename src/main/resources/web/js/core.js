export function utilsApp() {
  return {
      showToast(message, isError = false) {
          Toastify({
              text: message,
              duration: 3000,
              close: true,
              gravity: "top",
              position: "right",
              backgroundColor: isError ? "#e53e3e" : "#10b981",
              stopOnFocus: true
          }).showToast();
      },

      changeState(name) {
        const newValue = !this[name];
        localStorage.setItem(name, newValue);
        this[name] = newValue;
      },

      loadState(name) {
        const currValue = localStorage.getItem(name);
        this[name] = !!currValue;
      },

      backupValue(name, newValue) {
        const valueToStore = typeof newValue === 'object' ? JSON.stringify(newValue) : newValue;
        localStorage.setItem(name, valueToStore);
      },

      changeValue(name, newValue) {
        this.backupValue(name, newValue);
        this[name] = newValue;
      },

      loadValue(name, defaultValue) {
        const currValue = localStorage.getItem(name);
        try {
          this[name] = JSON.parse(currValue) || defaultValue;
        } catch (e) {
          this[name] = currValue || defaultValue;
        }
      },
  }
}
