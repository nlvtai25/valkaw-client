export namespace main {
	
	export class Config {
	    launcherVersion: string;
	    javaPath: string;
	    gameDir: string;
	    version: string;
	    profile: string;
	    gpu: string;
	    username: string;
	    memoryMB: number;
	    windowWidth: number;
	    windowHeight: number;
	    autoUpdate: boolean;
	    afterRun: string;
	
	    static createFrom(source: any = {}) {
	        return new Config(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.launcherVersion = source["launcherVersion"];
	        this.javaPath = source["javaPath"];
	        this.gameDir = source["gameDir"];
	        this.version = source["version"];
	        this.profile = source["profile"];
	        this.gpu = source["gpu"];
	        this.username = source["username"];
	        this.memoryMB = source["memoryMB"];
	        this.windowWidth = source["windowWidth"];
	        this.windowHeight = source["windowHeight"];
	        this.autoUpdate = source["autoUpdate"];
	        this.afterRun = source["afterRun"];
	    }
	}
	export class GPU {
	    id: string;
	    name: string;
	    vendor: string;
	    address: string;
	
	    static createFrom(source: any = {}) {
	        return new GPU(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.name = source["name"];
	        this.vendor = source["vendor"];
	        this.address = source["address"];
	    }
	}

}

