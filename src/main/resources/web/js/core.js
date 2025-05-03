export function utilsApp() {
  return {
      debounce(callback, delay = 300) {
          let timeoutId;

          const debounced = function(...args) {
              var context = this;
              clearTimeout(timeoutId);

              timeoutId = setTimeout(() => callback.apply(context, args), delay);
          };

          debounced.cancel = function() {
              clearTimeout(timeoutId);
          };

          return debounced;
      },

      withDebounce(key, fn, delay) {
          if(this[key]) {
              this[key].cancel();
              this[key] = undefined;
          }

          this[key] = debounce(fn, delay);
          this[key]();
      },

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

      changeValue(name, newValue) {
        localStorage.setItem(name, newValue);
        this[name] = newValue;
      },

      loadValue(name, defaultValue) {
        const currValue = localStorage.getItem(name);
        this[name] = currValue || defaultValue;
      }
  }
}
