package mindustry.maps;

import arc.assets.AssetDescriptor;
import arc.assets.AssetManager;
import arc.assets.loaders.TextureLoader;
import arc.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import arc.files.Fi;
import arc.graphics.Texture;
import arc.struct.Array;
import arc.util.Log;
import mindustry.Vars;
import mindustry.ctype.Content;

public class MapPreviewLoader extends TextureLoader{

    public MapPreviewLoader(){
        super(new AbsoluteFileHandleResolver());
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, Fi file, TextureParameter parameter){
        try{
            super.loadAsync(manager, fileName, file.sibling(file.nameWithoutExtension()), parameter);
        }catch(Exception e){
            Log.err(e);
            MapPreviewParameter param = (MapPreviewParameter)parameter;
            Vars.maps.queueNewPreview(param.map);
        }
    }

    @Override
    public Texture loadSync(AssetManager manager, String fileName, Fi file, TextureParameter parameter){
        try{
            return super.loadSync(manager, fileName, file, parameter);
        }catch(Throwable e){
            Log.err(e);
            try{
                return new Texture(file);
            }catch(Throwable e2){
                Log.err(e2);
                return new Texture("sprites/error.png");
            }
        }
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, Fi file, TextureParameter parameter){
        return Array.with(new AssetDescriptor<>("contentcreate", Content.class));
    }

    public static class MapPreviewParameter extends TextureParameter{
        public Map map;

        public MapPreviewParameter(Map map){
            this.map = map;
        }
    }
}
