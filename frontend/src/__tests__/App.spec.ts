import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '@/App.vue'

describe('App', () => {
  it('renders the project shell', () => {
    const wrapper = mount(App, {
      global: {
        plugins: [createPinia()],
        stubs: {
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: true,
        },
      },
    })

    expect(wrapper.text()).toContain('Realtime Tile Game')
    expect(wrapper.text()).toContain('Phase 4')
  })
})
