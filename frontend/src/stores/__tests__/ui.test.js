import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUiStore } from '../ui'

describe('ui store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('openLogin / closeLogin 切换登录弹窗可见性', () => {
    const store = useUiStore()
    expect(store.loginDialogVisible).toBe(false)

    store.openLogin()
    expect(store.loginDialogVisible).toBe(true)

    store.closeLogin()
    expect(store.loginDialogVisible).toBe(false)
  })
})
