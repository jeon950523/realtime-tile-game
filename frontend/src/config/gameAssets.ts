export interface GameAssetSet {
  boardTexture?: string
  rackTexture?: string
  drawIcon?: string
  passIcon?: string
  menuIcon?: string
  defaultAvatar?: string
}

// Licensed or replacement art can be registered here without changing game logic or components.
export const gameAssets: Readonly<GameAssetSet> = Object.freeze({})

