import { createApp } from 'vue'

import App from './App.vue'
import { configureHttpAuthentication } from './api/httpClient'
import router from './router'
import { useAuthStore } from './stores/auth'
import { pinia } from './stores/pinia'
import './assets/main.css'

const app = createApp(App)
const authStore = useAuthStore(pinia)

configureHttpAuthentication({
  getAccessToken: () => authStore.accessToken,
  acceptRefreshedAccessToken: (accessToken) => authStore.acceptRefreshedAccessToken(accessToken),
  authenticationLost: () => authStore.clearAuthentication(),
})

app.use(pinia)
app.use(router)
app.mount('#app')
